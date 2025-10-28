package com.example.application;

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

import static java.time.Duration.ofMinutes;

import java.util.stream.Stream;

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
  public WorkflowSettings settings() {
    return WorkflowSettings.builder()
        .defaultStepTimeout(ofMinutes(10))
        .build();
  }

  @Override
  public State emptyState() {
    return AgentPlayer.State.empty();
  }

  public Effect<Done> gameCreated(DotGame.Event.GameCreated event) {
    log.debug("WorkflowId: {}\n_State: {}\n_Event: {}", workflowId, currentState(), event);

    return effects()
        .transitionTo(AgentPlayerWorkflow::gameCreatedStep)
        .withInput(event)
        .thenReply(Done.getInstance());
  }

  public Effect<Done> moveMade(DotGame.Event.MoveMade event) {
    log.debug("WorkflowId: {}\n_State: {}\n_Event: {}", workflowId, currentState(), event);

    return effects()
        .transitionTo(AgentPlayerWorkflow::makeMoveStep)
        .withInput(event)
        .thenReply(Done.getInstance());
  }

  public Effect<Done> moveForfeited(DotGame.Event.MoveForfeited event) {
    log.debug("WorkflowId: {}\n_State: {}\n_Event: {}", workflowId, currentState(), event);

    return effects()
        .transitionTo(AgentPlayerWorkflow::forfeitMoveStep)
        .withInput(event)
        .thenReply(Done.getInstance());
  }

  public Effect<Done> postGameReview(DotGame.Event.GameFinished event) {
    log.debug("WorkflowId: {}\n_State: {}\n_Event: {}", workflowId, currentState(), event);

    return effects()
        .transitionTo(AgentPlayerWorkflow::postGameReviewStep)
        .withInput(event)
        .thenReply(Done.getInstance());
  }

  public Effect<Done> canceledGameReview(DotGame.Event.GameCanceled event) {
    log.debug("WorkflowId: {}\n_State: {}\n_Event: {}", workflowId, currentState(), event);

    return effects()
        .transitionTo(AgentPlayerWorkflow::canceledGameReviewStep)
        .withInput(event)
        .thenReply(Done.getInstance());
  }

  StepEffect gameCreatedStep(DotGame.Event.GameCreated event) {
    var agentPlayer = event.currentPlayerStatus().get();
    var sessionId = AgentPlayer.sessionId(event.gameId(), agentPlayer.player().id());

    var prompt = makeMovePromptFor(sessionId, event.gameId(), agentPlayer.player());

    makeMove(sessionId, prompt, "Make move (1 game created)");

    return stepEffects()
        .updateState(currentState().with(sessionId, event.gameId(), agentPlayer.player()))
        .thenPause();
  }

  StepEffect makeMoveStep(DotGame.Event.MoveMade event) {
    gameLog.logLastMove(event);

    var agentPlayer = event.currentPlayerStatus().get();
    var sessionId = AgentPlayer.sessionId(event.gameId(), agentPlayer.player().id());

    var prompt = makeMovePromptFor(sessionId, event.gameId(), agentPlayer.player());

    makeMove(sessionId, prompt, "Make move (2 game in progress)");

    return stepEffects()
        .updateState(currentState().with(sessionId, event.gameId(), agentPlayer.player()))
        .thenPause();
  }

  StepEffect forfeitMoveStep(DotGame.Event.MoveForfeited event) {
    gameLog.logForfeitMove(event);

    var currentPlayer = event.currentPlayerStatus();

    if (currentPlayer.isPresent() && currentPlayer.get().player().isAgent()) {
      var agentPlayer = currentPlayer.get();
      var sessionId = AgentPlayer.sessionId(event.gameId(), agentPlayer.player().id());

      var prompt = makeMovePromptFor(sessionId, event.gameId(), agentPlayer.player());

      makeMove(sessionId, prompt, "Make move (3 move forfeited)");
    }

    return stepEffects().thenPause();
  }

  StepEffect canceledGameReviewStep(DotGame.Event.GameCanceled event) {
    gameLog.logGameCanceled(currentState().agent().id(), event);

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

  StepEffect postGameReviewStep(DotGame.Event.GameFinished event) {
    gameLog.logGameFinished(currentState().agent().id(), event);

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

    return stepEffects()
        .updateState(currentState().withPlaybookReview(playbookReview))
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

    gameLog.logModelResponse(currentState().gameId(), currentState().agent().id(), systemPromptReview);

    return stepEffects()
        .updateState(currentState().withSystemPromptReview(systemPromptReview))
        .thenEnd();
  }

  void makeMove(String sessionId, AgentPlayerMakeMoveAgent.MakeMovePrompt prompt, String logMessage) {
    Stream.iterate(0, i -> i + 1)
        .map(i -> {
          return (i > 2)
              ? forfeitMove(i, sessionId, prompt, logMessage)
              : makeMoveAttempt(i, sessionId, prompt, logMessage);
        })
        .filter(Boolean::booleanValue)
        .findFirst(); // keep trying until the agent makes a move or too many attempts
  }

  boolean makeMoveAttempt(int i, String sessionId, AgentPlayerMakeMoveAgent.MakeMovePrompt prompt, String logMessage) {
    log.debug("Make move attempt: {}, agentId: {}", i + 1, prompt.agent().id());

    var response = "";
    try {
      response = componentClient
          .forAgent()
          .inSession(sessionId)
          .method(AgentPlayerMakeMoveAgent::makeMove)
          .invoke(prompt);
    } catch (Throwable e) {
      log.error("Exception making move attempt: {}, agentId: {}", i + 1, prompt.agent().id(), e);
      return false;
    }

    var gameState = componentClient
        .forEventSourcedEntity(prompt.gameId())
        .method(DotGameEntity::getState)
        .invoke();

    var playerId = prompt.agent().id();
    var agentMadeMove = gameState.currentPlayerStatus().isEmpty() || !gameState.currentPlayerStatus().get().player().id().equals(playerId);

    log.debug("{}, agent response: {}", logMessage, response);
    log.debug("Game status: {}, game over or agent: {} made move: {}", gameState.status(), playerId, agentMadeMove);

    gameLog.logModelResponse(prompt.gameId(), playerId, response);

    if (agentMadeMove && gameState.status() == DotGame.Status.in_progress) {
      var command = new DotGame.Command.PlayerTurnCompleted(prompt.gameId(), prompt.agent().id());
      componentClient
          .forEventSourcedEntity(prompt.gameId())
          .method(DotGameEntity::playerTurnCompleted)
          .invoke(command);
    }

    return agentMadeMove;
  }

  boolean forfeitMove(int i, String sessionId, AgentPlayerMakeMoveAgent.MakeMovePrompt prompt, String logMessage) {
    var playerId = prompt.agent().id();
    log.debug("{}, forfeit move after {} failed attempts, agentId: {}", logMessage, i + 1, playerId);

    var message = "Agent: %s, forfeited move after %d failed attempts".formatted(playerId, i + 1);
    var command = new DotGame.Command.ForfeitMove(prompt.gameId(), playerId, message);

    componentClient
        .forEventSourcedEntity(prompt.gameId())
        .method(DotGameEntity::forfeitMove)
        .invoke(command);

    return true;
  }

  AgentPlayerMakeMoveAgent.MakeMovePrompt makeMovePromptFor(String sessionId, String gameId, DotGame.Player agent) {
    return new AgentPlayerMakeMoveAgent.MakeMovePrompt(
        sessionId,
        gameId,
        agent);
  }
}
