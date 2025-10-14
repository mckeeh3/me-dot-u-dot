package com.example.application;

import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.DotGame;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;

@Component(id = "dot-game-to-agent-consumer")
@Consume.FromEventSourcedEntity(DotGameEntity.class)
public class DotGameToAgentConsumer extends Consumer {
  final Logger log = LoggerFactory.getLogger(getClass());
  final ComponentClient componentClient;
  final GameActionLogger gameLog;

  public DotGameToAgentConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
    this.gameLog = new GameActionLogger(componentClient);
  }

  public Effect onEvent(DotGame.Event event) {
    log.debug("Event: {}", event);

    return switch (event) {
      case DotGame.Event.GameCreated e -> onEvent(e);
      case DotGame.Event.MoveMade e -> onEvent(e);
      case DotGame.Event.GameCanceled e -> onEvent(e);
      case DotGame.Event.MoveForfeited e -> onEvent(e);
      default -> effects().done();
    };
  }

  Effect onEvent(DotGame.Event.GameCreated event) {
    var currentPlayer = event.currentPlayerStatus();

    if (currentPlayer.isPresent() && currentPlayer.get().player().isAgent()) {
      var agentPlayer = currentPlayer.get();
      var sessionId = sessionId(event.gameId(), agentPlayer.player().id());

      var prompt = makeMovePromptFor(event, agentPlayer);

      makeMove(sessionId, prompt, "Make move (1 game created)");
    }

    return effects().done();
  }

  Effect onEvent(DotGame.Event.MoveMade event) {
    var currentPlayer = event.currentPlayerStatus();

    gameLog.logMove(event);

    // game in progress
    if (event.status() == DotGame.Status.in_progress && currentPlayer.isPresent() && currentPlayer.get().player().isAgent()) {
      var agentPlayer = currentPlayer.get();
      var sessionId = sessionId(event.gameId(), agentPlayer.player().id());

      var prompt = makeMovePromptFor(event, agentPlayer);

      makeMove(sessionId, prompt, "Make move (2 game in progress)");

      return effects().done();
    }

    // game over
    if (!event.status().equals(DotGame.Status.in_progress)) {
      if (event.player1Status().player().isAgent()) {
        var agentPlayer = event.player1Status();
        var sessionId = sessionId(event.gameId(), agentPlayer.player().id());

        var prompt = makeMovePromptFor(event, agentPlayer);

        makeMove(sessionId, prompt, "Make move (3 game over, you " + (agentPlayer.isWinner() ? "won" : "lost") + ")");
      }

      if (event.player2Status().player().isAgent()) {
        var agentPlayer = event.player2Status();
        var sessionId = sessionId(event.gameId(), agentPlayer.player().id());

        var prompt = makeMovePromptFor(event, agentPlayer);

        makeMove(sessionId, prompt, "Make move (4 game over, you " + (agentPlayer.isWinner() ? "won" : "lost") + ")");
      }

      return effects().done();
    }

    return effects().done();
  }

  Effect onEvent(DotGame.Event.MoveForfeited event) {
    var currentPlayer = event.currentPlayerStatus();

    gameLog.logForfeitMove(event);

    if (event.status() == DotGame.Status.in_progress && currentPlayer.isPresent() && currentPlayer.get().player().isAgent()) {
      var agentPlayer = event.currentPlayerStatus().get();
      var sessionId = sessionId(event.gameId(), agentPlayer.player().id());

      var prompt = makeMovePromptFor(event, agentPlayer);

      makeMove(sessionId, prompt, "Make move (5 move forfeited)");
    }

    return effects().done();
  }

  Effect onEvent(DotGame.Event.GameCanceled event) {
    if (event.player1Status().player().isAgent()) {
      var agentPlayer = event.player1Status();
      var sessionId = sessionId(event.gameId(), agentPlayer.player().id());

      var prompt = makeMovePromptFor(event, agentPlayer);

      makeMove(sessionId, prompt, "Make move (6 game canceled)");
    }

    if (event.player2Status().player().isAgent()) {
      var agentPlayer = event.player2Status();
      var sessionId = sessionId(event.gameId(), agentPlayer.player().id());

      var prompt = makeMovePromptFor(event, agentPlayer);

      makeMove(sessionId, prompt, "Make move (7 game canceled)");
    }

    return effects().done();
  }

  void makeMove(String sessionId, DotGameAgent.MakeMovePrompt prompt, String logMessage) {
    Stream.iterate(0, i -> i + 1)
        .map(i -> {
          return (i > 2)
              ? forfeitMoveAttempt(i, sessionId, prompt, logMessage)
              : makeMoveAttempt(i, sessionId, prompt, logMessage);
        })
        .filter(Boolean::booleanValue)
        .findFirst(); // keep trying until the agent makes a move or too many attempts
  }

  boolean makeMoveAttempt(int i, String sessionId, DotGameAgent.MakeMovePrompt prompt, String logMessage) {
    log.debug("Make move attempt: {}, agentId: {}", i + 1, prompt.playerStatus().player().id());

    var response = "";
    try {
      response = componentClient
          .forAgent()
          .inSession(sessionId)
          .method(DotGameAgent::makeMove)
          .invoke(prompt);
    } catch (Throwable e) {
      log.error("Exception making move attempt: {}, agentId: {}", i + 1, prompt.playerStatus().player().id(), e);
      return false;
    }

    var gameState = componentClient
        .forEventSourcedEntity(prompt.gameId())
        .method(DotGameEntity::getState)
        .invoke();

    var playerId = prompt.playerStatus().player().id();
    var agentMadeMove = gameState.currentPlayer().isEmpty() || !gameState.currentPlayer().get().player().id().equals(playerId);

    log.debug("{}, agent response: {}", logMessage, response);
    log.debug("Game status: {}, game over or agent: {} made move: {}", gameState.status(), playerId, agentMadeMove);

    gameLog.logModelResponse(prompt.gameId(), playerId, response);

    return agentMadeMove;
  }

  boolean forfeitMoveAttempt(int i, String sessionId, DotGameAgent.MakeMovePrompt prompt, String logMessage) {
    var playerId = prompt.playerStatus().player().id();
    log.debug("{}, forfeit move after {} failed attempts, agentId: {}", logMessage, i + 1, playerId);

    var message = "Agent: %s, forfeited move after %d failed attempts".formatted(playerId, i + 1);
    var command = new DotGame.Command.ForfeitMove(prompt.gameId(), playerId, message);

    componentClient
        .forEventSourcedEntity(prompt.gameId())
        .method(DotGameEntity::forfeitMove)
        .invoke(command);

    return true;
  }

  static String sessionId(String gameId, String playerId) {
    return gameId + "/" + playerId;
  }

  DotGameAgent.MakeMovePrompt makeMovePromptFor(DotGame.Event.GameCreated event, DotGame.PlayerStatus agentPlayer) {
    return new DotGameAgent.MakeMovePrompt(
        event.gameId(),
        event.status(),
        agentPlayer,
        0);
  }

  DotGameAgent.MakeMovePrompt makeMovePromptFor(DotGame.Event.MoveMade event, DotGame.PlayerStatus agentPlayer) {
    var opponentScore = event.player1Status().score() + event.player2Status().score() - agentPlayer.score();

    return new DotGameAgent.MakeMovePrompt(
        event.gameId(),
        event.status(),
        agentPlayer,
        opponentScore);
  }

  DotGameAgent.MakeMovePrompt makeMovePromptFor(DotGame.Event.MoveForfeited event, DotGame.PlayerStatus agentPlayer) {
    var gameState = componentClient
        .forEventSourcedEntity(event.gameId())
        .method(DotGameEntity::getState)
        .invoke();

    var opponentScore = gameState.player1Status().score() + gameState.player2Status().score() - agentPlayer.score();

    return new DotGameAgent.MakeMovePrompt(
        event.gameId(),
        event.status(),
        agentPlayer,
        opponentScore);
  }

  DotGameAgent.MakeMovePrompt makeMovePromptFor(DotGame.Event.GameCanceled event, DotGame.PlayerStatus agentPlayer) {
    var opponentScore = event.player1Status().score() + event.player2Status().score() - agentPlayer.score();

    return new DotGameAgent.MakeMovePrompt(
        event.gameId(),
        event.status(),
        agentPlayer,
        opponentScore);
  }
}
