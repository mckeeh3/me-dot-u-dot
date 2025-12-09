package com.example.application;

import static java.time.Duration.ofMinutes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.AgentPlayer;
import com.example.domain.AgentPlayer.State;
import com.example.domain.DotGame;

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
      var agentPlayer = event.currentPlayerStatus().get();
      var sessionId = AgentPlayer.sessionId(event.gameId(), agentPlayer.player().id());

      state = state
          .with(sessionId, event.gameId(), agentPlayer.player());
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
          .transitionTo(AgentPlayerWorkflow::startGameReviewStep)
          .withInput(event)
          .thenReply(Done.getInstance());
    }

    return effects().reply(Done.getInstance()); // ignore duplicate messages
  }

  StepEffect makeMoveStep(DotGame.Event.PlayerTurnCompleted event) {
    log.debug("Make move step: {}\n_state: {}", event.gameId(), currentState());

    var sessionId = AgentPlayer.sessionId(event.gameId(), currentState().agent().id());
    var prompt = makeMovePromptFor(sessionId, event.gameId(), currentState().agent());

    if (currentState().stepRetryCount() > 3) {
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

    log.debug("Make move step response: {}\n_agent player response: {}\n_state: {}", event.gameId(), response, currentState());

    if (response.startsWith("Forfeit move, ")) { // this is not ideal, we should have a more robust way to handle this
      return stepEffects()
          .updateState(currentState().resetStepRetryCount())
          .thenTransitionTo(AgentPlayerWorkflow::forfeitMoveStep)
          .withInput(event);
    }

    return stepEffects()
        .updateState(currentState().withMoveCount(event.moveHistory().size()))
        .thenTransitionTo(AgentPlayerWorkflow::verifyMoveStep)
        .withInput(event);
  }

  StepEffect verifyMoveStep(DotGame.Event.PlayerTurnCompleted event) {
    log.debug("Verify move step: {}\n_state: {}", event.gameId(), currentState());

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
    log.debug("Move completed step: {}\n_state: {}", event.gameId(), currentState());

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
    var message = "Agent: %s, forfeited move after %d failed attempts".formatted(currentState().agent().id(), currentState().stepRetryCount());
    var prompt = new AgentPlayerPlaybookReviewAgent.PlaybookReviewPrompt(currentState().sessionId(), currentState().gameId(), currentState().agent(), message);
    var playerId = prompt.agent().id();
    var command = new DotGame.Command.ForfeitMove(prompt.gameId(), playerId, message);

    componentClient
        .forEventSourcedEntity(prompt.gameId())
        .method(DotGameEntity::forfeitMove)
        .invoke(command);

    return stepEffects()
        .updateState(currentState().resetStepRetryCount())
        .thenPause();
  }

  StepEffect startGameReviewStep(DotGame.Event.PlayerTurnCompleted event) {
    log.debug("Start game review step: {}, move count: {}", event.gameId(), event.moveHistory().size());

    var prompt = new AgentPlayerPostGameReviewAgent.PostGameReviewPrompt(currentState().sessionId(), currentState().gameId(), currentState().agent());

    var gameReview = componentClient
        .forAgent()
        .inSession(currentState().sessionId())
        .method(AgentPlayerPostGameReviewAgent::postGameReview)
        .invoke(prompt);

    gameLog.logModelResponse(currentState().gameId(), currentState().agent().id(), gameReview);

    return stepEffects()
        .updateState(currentState().withGameReview(gameReview))
        .thenTransitionTo(AgentPlayerWorkflow::postGamePlaybookReviewStep)
        .withInput(gameReview);
  }

  StepEffect postGamePlaybookReviewStep(String gameReview) {
    var prompt = new AgentPlayerPlaybookReviewAgent.PlaybookReviewPrompt(currentState().sessionId(), currentState().gameId(), currentState().agent(), gameReview);

    var playbookReview = componentClient
        .forAgent()
        .inSession(currentState().sessionId())
        .method(AgentPlayerPlaybookReviewAgent::playbookReview)
        .invoke(prompt);

    gameLog.logModelResponse(currentState().gameId(), currentState().agent().id(), playbookReview);

    var playbook = componentClient
        .forEventSourcedEntity(currentState().agent().id())
        .method(PlaybookEntity::getState)
        .invoke();

    if (playbook.instructions().isEmpty() && currentState().stepRetryCount() < 3) {
      return stepEffects()
          .updateState(currentState().incrementStepRetryCount().withPlaybookReview(playbookReview))
          .thenTransitionTo(AgentPlayerWorkflow::postGamePlaybookReviewStep)
          .withInput(gameReview);
    }

    return stepEffects()
        .updateState(currentState().resetStepRetryCount().withPlaybookReview(playbookReview))
        .thenTransitionTo(AgentPlayerWorkflow::postGameSystemPromptReviewStep)
        .withInput(gameReview);
  }

  StepEffect postGameSystemPromptReviewStep(String gameReview) {
    var prompt = new AgentPlayerSystemPromptReviewAgent.SystemPromptReviewPrompt(currentState().sessionId(), currentState().gameId(), currentState().agent(), gameReview);

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
    log.error("WorkflowId: {}\n_Cancelling game\n_State: {}", workflowId, currentState());

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
