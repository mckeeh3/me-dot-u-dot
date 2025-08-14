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
      case DotGame.Event.GameCompleted e -> onEvent(e);
    };
  }

  Effect onEvent(DotGame.Event.GameCreated event) {
    var currentPlayer = event.currentPlayerStatus();

    if (currentPlayer.isPresent() && currentPlayer.get().player().isAgent()) {
      var player1 = event.player1Status();
      var player2 = event.player2Status();

      var agentPlayer = currentPlayer.get();
      var opponentPlayer = agentPlayer.player().id().equals(player1.player().id()) ? player2 : player1;

      var prompt = new DotGameAgent.MakeMovePrompt(
          event.gameId(),
          event.board(),
          event.status(),
          agentPlayer,
          opponentPlayer,
          event.moveHistory());

      componentClient
          .forAgent()
          .inSession(event.gameId())
          .method(DotGameAgent::makeMove)
          .invoke(prompt);
    }

    return effects().done();
  }

  Effect onEvent(DotGame.Event.MoveMade event) {
    var currentPlayer = event.currentPlayerStatus();

    if (currentPlayer.isPresent() && currentPlayer.get().player().isAgent()) {
      var player1 = event.player1Status();
      var player2 = event.player2Status();

      var agentPlayer = currentPlayer.get();
      var opponentPlayer = agentPlayer.player().id().equals(player1.player().id()) ? player2 : player1;

      var prompt = new DotGameAgent.MakeMovePrompt(
          event.gameId(),
          event.board(),
          event.status(),
          agentPlayer,
          opponentPlayer,
          event.moveHistory());

      componentClient
          .forAgent()
          .inSession(event.gameId())
          .method(DotGameAgent::makeMove)
          .invoke(prompt);
    }

    return effects().done();
  }

  Effect onEvent(DotGame.Event.GameCompleted event) {
    // Fetch final state to include board and move history
    var state = componentClient
        .forEventSourcedEntity(event.gameId())
        .method(DotGameEntity::getState)
        .invoke();

    var player1 = event.player1Status();
    var player2 = event.player2Status();
    var current = player1.isWinner() ? player1 : (player2.isWinner() ? player2 : player1);
    var opponentPlayer = current.player().id().equals(player1.player().id()) ? player2 : player1;

    var prompt = new DotGameAgent.MakeMovePrompt(
        event.gameId(),
        state.board(),
        event.status(),
        current,
        opponentPlayer,
        state.moveHistory());

    componentClient
        .forAgent()
        .inSession(event.gameId())
        .method(DotGameAgent::makeMove)
        .invoke(prompt);

    return effects().done();
  }
}
