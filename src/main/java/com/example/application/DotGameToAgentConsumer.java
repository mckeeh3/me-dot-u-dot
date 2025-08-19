package com.example.application;

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
      case DotGame.Event.GameCanceled e -> effects().done(); // skip event
      case DotGame.Event.MoveForfeited e -> effects().done(); // skip event
    };
  }

  Effect onEvent(DotGame.Event.GameCreated event) {
    var currentPlayer = event.currentPlayerStatus();

    if (currentPlayer.isPresent() && currentPlayer.get().player().isAgent()) {
      var agentPlayer = currentPlayer.get();

      var prompt = new DotGameAgent.MakeMovePrompt(
          event.gameId(),
          event.status(),
          agentPlayer.player().id(),
          agentPlayer.player().name(),
          agentPlayer.player().model());

      var result = componentClient
          .forAgent()
          .inSession(event.gameId())
          .method(DotGameAgent::makeMove)
          .invoke(prompt);

      log.debug("Make move (1 game created) result: {}", result);
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

      var result = componentClient
          .forAgent()
          .inSession(sessionId)
          .method(DotGameAgent::makeMove)
          .invoke(prompt);

      log.debug("Make move (2 game in progress) result: {}", result);
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

        var result = componentClient
            .forAgent()
            .inSession(sessionId)
            .method(DotGameAgent::makeMove)
            .invoke(prompt);

        log.debug("Make move (3 game over, you {}) result: {}", agentPlayer.isWinner() ? "won" : "lost", result);
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

        var result = componentClient
            .forAgent()
            .inSession(sessionId)
            .method(DotGameAgent::makeMove)
            .invoke(prompt);

        log.debug("Make move (4 game over, you {}) result: {}", agentPlayer.isWinner() ? "won" : "lost", result);
      }

      return effects().done();
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

      var result = componentClient
          .forAgent()
          .inSession(sessionId)
          .method(DotGameAgent::makeMove)
          .invoke(prompt);

      log.debug("Make move (5 game canceled, you {}) result: {}", agentPlayer.isWinner() ? "won" : "lost", result);
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

      var result = componentClient
          .forAgent()
          .inSession(sessionId)
          .method(DotGameAgent::makeMove)
          .invoke(prompt);

      log.debug("Make move (6 game canceled, you {}) result: {}", agentPlayer.isWinner() ? "won" : "lost", result);
    }

    return effects().done();
  }
}
