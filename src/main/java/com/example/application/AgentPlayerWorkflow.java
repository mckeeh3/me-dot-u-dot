package com.example.application;

import static java.time.Duration.ofMinutes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.AgentPlayer;
import com.example.domain.AgentPlayer.State;
import com.example.domain.DotGame;
import com.example.domain.GameLog;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import akka.javasdk.workflow.WorkflowContext;

@Component(id = "agent-player-workflow")
public class AgentPlayerWorkflow extends Workflow<AgentPlayer.State> {
  static final Logger log = LoggerFactory.getLogger(AgentPlayerWorkflow.class);
  final ComponentClient componentClient;
  final GameActionLogger gameLog;
  final String workflowId;

  public AgentPlayerWorkflow(ComponentClient componentClient, WorkflowContext workflowContext) {
    this.componentClient = componentClient;
    this.gameLog = new GameActionLogger(componentClient);
    this.workflowId = workflowContext.workflowId();
  }

  @Override
  public State emptyState() {
    return AgentPlayer.State.empty();
  }

  @Override
  public WorkflowSettings settings() {
    return WorkflowSettings.builder()
        .defaultStepTimeout(ofMinutes(10))
        .defaultStepRecovery(maxRetries(0).failoverTo(AgentPlayerWorkflow::cancelGameStep))
        .stepRecovery(
            AgentPlayerWorkflow::makeMoveStep,
            maxRetries(0).failoverTo(AgentPlayerWorkflow::cancelGameStep))
        .build();
  }

  public Effect<Done> playerTurnCompleted(DotGame.Event.PlayerTurnCompleted event) {
    log.debug("WorkflowId: {}\n_State: {}\n_Event: {}", workflowId, currentState(), event);

    var state = currentState();
    if (state.isEmpty()) {
      var agentPlayerStatus = event.currentPlayerStatus().get();
      var sessionId = AgentPlayer.sessionId(event.gameId(), agentPlayerStatus.player().id());

      state = state
          .with(sessionId, event.gameId(), agentPlayerStatus.player());
    }

    if (DotGame.Status.in_progress == event.status() && currentState().moveCount() < event.moveHistory().size()) { // de-dup check
      return effects()
          .updateState(state)
          .transitionTo(AgentPlayerWorkflow::makeMoveStep)
          .withInput(event)
          .thenReply(Done.getInstance());
    }

    if (DotGame.Status.in_progress != event.status() && currentState().moveCount() < event.moveHistory().size()) { // de-dup check
      return effects()
          .updateState(state)
          .transitionTo(AgentPlayerWorkflow::startPostGameReviewStep)
          .withInput(event)
          .thenReply(Done.getInstance());
    }

    return effects().reply(Done.getInstance()); // ignore duplicate messages
  }

  StepEffect makeMoveStep(DotGame.Event.PlayerTurnCompleted event) {
    log.debug("Make move step, WorkflowId: {}\n_state: {}", workflowId, currentState());

    var agentId = currentState().agent().id();
    var sessionId = AgentPlayer.sessionId(event.gameId(), agentId);
    var prompt = makeMovePromptFor(sessionId, event.gameId(), currentState().agent());

    if (currentState().stepRetryCount() > 3) {
      log.debug("Make move step, WorkflowId: {}\n_state: {}\n_forfeiting move due to too many retries", workflowId, currentState());
      gameLog.logError(event.gameId(), agentId, "Make move step: forfeiting move due to too many retries");

      return stepEffects()
          .updateState(currentState().resetStepRetryCount())
          .thenTransitionTo(AgentPlayerWorkflow::forfeitMoveStep)
          .withInput(event);
    }

    var response = componentClient
        .forAgent()
        .inSession(sessionId)
        .method(AgentPlayerMakeMoveAgent::makeMove)
        .invoke(prompt);

    log.debug("Make move step response, WorkflowId: {}\n_agent player response: {}\n_state: {}", workflowId, response, currentState());

    gameLog.logModelResponse(prompt.gameId(), agentId, response);

    if (response.startsWith("Forfeit move, ")) { // this is not ideal, we should have a more robust way to handle this
      log.debug("Make move step, WorkflowId: {}\n_state: {}\n_forfeiting move due to agent error", workflowId, currentState());
      gameLog.logError(event.gameId(), agentId, "Make move step: forfeiting move due to agent error");

      return stepEffects()
          .updateState(currentState().resetStepRetryCount())
          .thenTransitionTo(AgentPlayerWorkflow::forfeitMoveStep)
          .withInput(event);
    }

    var moveNumber = event.currentPlayerStatus().get().moves();
    var command = new GameLog.Command.CreateMakeMoveResponse(event.gameId(), agentId, moveNumber, response);
    componentClient
        .forEventSourcedEntity(event.gameId())
        .method(GameLogEntity::createMakeMoveResponse)
        .invoke(command);

    return stepEffects()
        .updateState(currentState().withMoveCount(event.moveHistory().size()))
        .thenTransitionTo(AgentPlayerWorkflow::verifyMoveStep)
        .withInput(event);
  }

  StepEffect verifyMoveStep(DotGame.Event.PlayerTurnCompleted event) {
    log.debug("Verify move step, WorkflowId: {}\n_state: {}", workflowId, currentState());

    var gameState = componentClient
        .forEventSourcedEntity(event.gameId())
        .method(DotGameEntity::getState)
        .invoke();

    var agentMadeMove = gameState.currentPlayerStatus().isEmpty() || !gameState.currentPlayerStatus().get().player().id().equals(currentState().agent().id());

    if (agentMadeMove) {
      return stepEffects()
          .updateState(currentState().resetStepRetryCount())
          .thenTransitionTo(AgentPlayerWorkflow::moveCompletedStep)
          .withInput(event);
    }

    return stepEffects()
        .updateState(currentState().incrementStepRetryCount())
        .thenTransitionTo(AgentPlayerWorkflow::makeMoveStep)
        .withInput(event);
  }

  StepEffect moveCompletedStep(DotGame.Event.PlayerTurnCompleted event) {
    log.debug("Move completed step, WorkflowId: {}\n_state: {}", workflowId, currentState());

    var command = new DotGame.Command.PlayerTurnCompleted(event.gameId(), currentState().agent().id());
    componentClient
        .forEventSourcedEntity(event.gameId())
        .method(DotGameEntity::playerTurnCompleted)
        .invoke(command);

    return stepEffects()
        .updateState(currentState().resetStepRetryCount())
        .thenPause();
  }

  StepEffect forfeitMoveStep(DotGame.Event.PlayerTurnCompleted event) {
    log.debug("Forfeit move step, WorkflowId: {}\n_state: {}", workflowId, currentState());

    var message = "Agent: %s, forfeited move after %d failed attempts".formatted(currentState().agent().id(), currentState().stepRetryCount());
    var playerId = currentState().agent().id();
    var command = new DotGame.Command.ForfeitMove(currentState().gameId(), playerId, message);

    componentClient
        .forEventSourcedEntity(currentState().gameId())
        .method(DotGameEntity::forfeitMove)
        .invoke(command);

    return stepEffects()
        .updateState(currentState().resetStepRetryCount())
        .thenPause();
  }

  StepEffect startPostGameReviewStep(DotGame.Event.PlayerTurnCompleted event) {
    log.debug("Start post game review step, WorkflowId: {}\n_state: {}", workflowId, currentState());

    var prompt = new AgentPlayerPostGameReviewAgent.PostGameReviewPrompt(currentState().sessionId(), currentState().gameId(), currentState().agent());

    var postGameReview = componentClient
        .forAgent()
        .inSession(currentState().sessionId())
        .method(AgentPlayerPostGameReviewAgent::postGameReview)
        .invoke(prompt);

    gameLog.logModelResponse(currentState().gameId(), currentState().agent().id(), postGameReview);

    return stepEffects()
        .updateState(currentState().withPostGameReview(postGameReview))
        .thenTransitionTo(AgentPlayerWorkflow::postGamePlaybookReviewStep)
        .withInput(postGameReview);
  }

  StepEffect postGamePlaybookReviewStep(String postGameReview) {
    log.debug("Post game playbook review step, WorkflowId: {}\n_state: {}", workflowId, currentState());

    var prompt = currentState().stepRetryCount() > 0
        ? AgentPlayerPlaybookReviewAgent.PlaybookReviewPrompt.with(currentState().sessionId(), currentState().gameId(), currentState().agent(), postGameReview)
        : AgentPlayerPlaybookReviewAgent.PlaybookReviewPrompt.withRetry();

    var playbookReview = componentClient
        .forAgent()
        .inSession(currentState().sessionId())
        .method(AgentPlayerPlaybookReviewAgent::playbookReview)
        .invoke(prompt);

    gameLog.logModelResponse(currentState().gameId(), currentState().agent().id(), playbookReview);

    return stepEffects()
        .updateState(currentState().resetStepRetryCount().withPlaybookReview(playbookReview))
        .thenTransitionTo(AgentPlayerWorkflow::verifyPlaybookNotEmptyStep)
        .withInput(postGameReview);
  }

  StepEffect verifyPlaybookNotEmptyStep(String postGameReview) {
    log.debug("Verify playbook not empty step, WorkflowId: {}\n_state: {}", workflowId, currentState());

    var playbook = componentClient
        .forEventSourcedEntity(currentState().agent().id())
        .method(PlaybookEntity::getState)
        .invoke();

    if (playbook.instructions().isEmpty() && currentState().stepRetryCount() < 3) {
      return stepEffects()
          .updateState(currentState()
              .incrementStepRetryCount()
              .withPlaybookReview(postGameReview))
          .thenTransitionTo(AgentPlayerWorkflow::postGamePlaybookReviewStep)
          .withInput(postGameReview);
    }

    return stepEffects()
        .updateState(currentState()
            .resetStepRetryCount()
            .withPlaybookReview(postGameReview))
        .thenTransitionTo(AgentPlayerWorkflow::postGameSystemPromptReviewStep)
        .withInput(postGameReview);
  }

  StepEffect postGameSystemPromptReviewStep(String postGameReview) {
    log.debug("Post game system prompt review step, WorkflowId: {}\n_state: {}", workflowId, currentState());

    var prompt = new AgentPlayerSystemPromptReviewAgent.SystemPromptReviewPrompt(currentState().sessionId(), currentState().gameId(), currentState().agent(), postGameReview);

    var systemPromptReview = componentClient
        .forAgent()
        .inSession(currentState().sessionId())
        .method(AgentPlayerSystemPromptReviewAgent::systemPromptReview)
        .invoke(prompt);

    gameLog.logModelResponse(currentState().gameId(), currentState().agent().id(), systemPromptReview.toString());

    return stepEffects()
        .updateState(currentState().withSystemPromptReview(systemPromptReview.toString()))
        .thenEnd();
  }

  StepEffect cancelGameStep() {
    log.error("Cancelling game step, WorkflowId: {}\n_State: {}", workflowId, currentState());

    var reason = "Workflow cancelled game due to unexpected error";
    var command = new DotGame.Command.CancelGame(currentState().gameId(), reason);

    componentClient
        .forEventSourcedEntity(currentState().gameId())
        .method(DotGameEntity::cancelGame)
        .invoke(command);

    return stepEffects()
        .thenEnd();
  }

  AgentPlayerMakeMoveAgent.MakeMovePrompt makeMovePromptFor(String sessionId, String gameId, DotGame.Player agent) {
    return new AgentPlayerMakeMoveAgent.MakeMovePrompt(
        sessionId,
        gameId,
        agent);
  }
}
