package com.example.application;

import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import com.example.domain.DotGame;

@ComponentId("dot-game-to-agent-consumer")
@Consume.FromEventSourcedEntity(DotGameEntity.class)
public class DotGameToAgentConsumer extends Consumer {
  final Logger log = LoggerFactory.getLogger(getClass());
  final ComponentClient componentClient;

  public DotGameToAgentConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
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
      var sessionId = event.gameId() + "-" + agentPlayer.player().id();

      var prompt = new DotGameAgent.MakeMovePrompt(
          event.gameId(),
          event.status(),
          agentPlayer.player().id(),
          agentPlayer.player().name(),
          agentPlayer.player().model());

      // var result = componentClient
      // .forAgent()
      // .inSession(sessionId)
      // .method(DotGameAgent::makeMove)
      // .invoke(prompt);

      // log.debug("Make move (1 game created) result: {}", result);

      makeMove(sessionId, prompt, "Make move (1 game created)");
    }

    return effects().done();
  }

  Effect onEvent(DotGame.Event.MoveMade event) {
    var currentPlayer = event.currentPlayerStatus();

    if (event.status() == DotGame.Status.in_progress && currentPlayer.isPresent() && currentPlayer.get().player().isAgent()) {
      var agentPlayer = currentPlayer.get();
      var sessionId = event.gameId() + "-" + agentPlayer.player().id();

      var prompt = new DotGameAgent.MakeMovePrompt(
          event.gameId(),
          event.status(),
          agentPlayer.player().id(),
          agentPlayer.player().name(),
          agentPlayer.player().model());

      // var result = componentClient
      // .forAgent()
      // .inSession(sessionId)
      // .method(DotGameAgent::makeMove)
      // .invoke(prompt);

      // log.debug("Make move (2 game in progress) result: {}", result);

      makeMove(sessionId, prompt, "Make move (2 game in progress)");

      return effects().done();
    }

    if (!event.status().equals(DotGame.Status.in_progress)) {
      if (event.player1Status().player().isAgent()) {
        var agentPlayer = event.player1Status();
        var sessionId = event.gameId() + "-" + agentPlayer.player().id();

        var prompt = new DotGameAgent.MakeMovePrompt(
            event.gameId(),
            event.status(),
            agentPlayer.player().id(),
            agentPlayer.player().name(),
            agentPlayer.player().model());

        // var result = componentClient
        // .forAgent()
        // .inSession(sessionId)
        // .method(DotGameAgent::makeMove)
        // .invoke(prompt);

        // log.debug("Make move (3 game over, you {}) result: {}", agentPlayer.isWinner() ? "won" : "lost", result);

        makeMove(sessionId, prompt, "Make move (3 game over, you " + (agentPlayer.isWinner() ? "won" : "lost") + ")");
      }

      if (event.player2Status().player().isAgent()) {
        var agentPlayer = event.player2Status();
        var sessionId = event.gameId() + "-" + agentPlayer.player().id();

        var prompt = new DotGameAgent.MakeMovePrompt(
            event.gameId(),
            event.status(),
            agentPlayer.player().id(),
            agentPlayer.player().name(),
            agentPlayer.player().model());

        // var result = componentClient
        // .forAgent()
        // .inSession(sessionId)
        // .method(DotGameAgent::makeMove)
        // .invoke(prompt);

        // log.debug("Make move (4 game over, you {}) result: {}", agentPlayer.isWinner() ? "won" : "lost", result);

        makeMove(sessionId, prompt, "Make move (4 game over, you " + (agentPlayer.isWinner() ? "won" : "lost") + ")");
      }

      return effects().done();
    }

    return effects().done();
  }

  Effect onEvent(DotGame.Event.MoveForfeited event) {
    var currentPlayer = event.currentPlayer();

    if (event.status() == DotGame.Status.in_progress && currentPlayer.isPresent() && currentPlayer.get().player().isAgent()) {
      var agentPlayer = event.currentPlayer().get();
      var sessionId = event.gameId() + "-" + agentPlayer.player().id();

      var prompt = new DotGameAgent.MakeMovePrompt(
          event.gameId(),
          event.status(),
          agentPlayer.player().id(),
          agentPlayer.player().name(),
          agentPlayer.player().model());

      // var result = componentClient
      // .forAgent()
      // .inSession(sessionId)
      // .method(DotGameAgent::makeMove)
      // .invoke(prompt);

      // log.debug("Make move (5 move forfeited) result: {}", result);

      makeMove(sessionId, prompt, "Make move (5 move forfeited)");
    }

    return effects().done();
  }

  Effect onEvent(DotGame.Event.GameCanceled event) {
    if (event.player1Status().player().isAgent()) {
      var agentPlayer = event.player1Status();
      var sessionId = event.gameId() + "-" + agentPlayer.player().id();

      var prompt = new DotGameAgent.MakeMovePrompt(
          event.gameId(),
          event.status(),
          agentPlayer.player().id(),
          agentPlayer.player().name(),
          agentPlayer.player().model());

      // var result = componentClient
      // .forAgent()
      // .inSession(sessionId)
      // .method(DotGameAgent::makeMove)
      // .invoke(prompt);

      // log.debug("Make move (6 game canceled) result: {}", result);

      makeMove(sessionId, prompt, "Make move (6 game canceled)");
    }

    if (event.player2Status().player().isAgent()) {
      var agentPlayer = event.player2Status();
      var sessionId = event.gameId() + "-" + agentPlayer.player().id();

      var prompt = new DotGameAgent.MakeMovePrompt(
          event.gameId(),
          event.status(),
          agentPlayer.player().id(),
          agentPlayer.player().name(),
          agentPlayer.player().model());

      // var result = componentClient
      // .forAgent()
      // .inSession(sessionId)
      // .method(DotGameAgent::makeMove)
      // .invoke(prompt);

      // log.debug("Make move (7 game canceled) result: {}", result);

      makeMove(sessionId, prompt, "Make move (7 game canceled)");
    }

    return effects().done();
  }

  void makeMove(String sessionId, DotGameAgent.MakeMovePrompt prompt, String logMessage) {
    Stream.iterate(0, i -> i + 1)
        .map(i -> {
          log.debug("Make move attempt: {}, agentId: {}", i + 1, prompt.agentId());

          var result = componentClient
              .forAgent()
              .inSession(sessionId)
              .method(DotGameAgent::makeMove)
              .invoke(prompt);

          var gameState = componentClient
              .forEventSourcedEntity(prompt.gameId())
              .method(DotGameEntity::getState)
              .invoke();

          var agentMadeMove = gameState.currentPlayer().isEmpty() || !gameState.currentPlayer().get().player().id().equals(prompt.agentId());

          log.debug("{}, agent result: {}", logMessage, result);
          log.debug("Game status: {}, game over or agent made move: {}", gameState.status(), agentMadeMove);

          return agentMadeMove;
        })
        .filter(Boolean::booleanValue)
        .findFirst(); // keep trying until the agent makes a move
  }
}
