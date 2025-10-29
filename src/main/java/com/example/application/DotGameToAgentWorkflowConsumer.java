package com.example.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.AgentPlayer;
import com.example.domain.DotGame;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;

@Component(id = "dot-game-to-agent-workflow-consumer")
@Consume.FromEventSourcedEntity(DotGameEntity.class)
public class DotGameToAgentWorkflowConsumer extends Consumer {
  final Logger log = LoggerFactory.getLogger(getClass());
  final ComponentClient componentClient;
  final GameActionLogger gameLog;

  public DotGameToAgentWorkflowConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
    this.gameLog = new GameActionLogger(componentClient);
  }

  public Effect onEvent(DotGame.Event event) {
    log.debug("Event: {}", event);

    return switch (event) {
      case DotGame.Event.PlayerTurnCompleted e -> onEvent(e);
      case DotGame.Event.GameCreated e -> onEvent(e);
      case DotGame.Event.MoveMade e -> onEvent(e);
      case DotGame.Event.MoveForfeited e -> onEvent(e);
      case DotGame.Event.GameFinished e -> onEvent(e);
      // case DotGame.Event.GameCanceled e -> onEvent(e);
      default -> effects().done();
    };
  }

  Effect onEvent(DotGame.Event.PlayerTurnCompleted event) {
    var currentPlayerStatus = event.currentPlayerStatus();

    log.debug("Player turn completed: {}, move count: {},\n_Current player status: {}", event.status(), event.moveHistory().size(), currentPlayerStatus);

    if (DotGame.Status.in_progress != event.status()) {
      if (event.player1Status().player().isAgent()) {
        var sessionId = AgentPlayer.sessionId(event.gameId(), event.player1Status().player().id());
        componentClient
            .forWorkflow(sessionId)
            .method(AgentPlayerWorkflow::playerTurnCompleted)
            .invoke(event);
      }

      if (event.player2Status().player().isAgent()) {
        var sessionId = AgentPlayer.sessionId(event.gameId(), event.player2Status().player().id());
        componentClient
            .forWorkflow(sessionId)
            .method(AgentPlayerWorkflow::playerTurnCompleted)
            .invoke(event);
      }
    }

    if (DotGame.Status.in_progress == event.status()) {
      if (currentPlayerStatus.isPresent() && currentPlayerStatus.get().player().isAgent()) {
        var agentPlayer = currentPlayerStatus.get();
        var sessionId = AgentPlayer.sessionId(event.gameId(), agentPlayer.player().id());
        componentClient
            .forWorkflow(sessionId)
            .method(AgentPlayerWorkflow::playerTurnCompleted)
            .invoke(event);
      }
    }

    return effects().done();
  }

  Effect onEvent(DotGame.Event.GameCreated event) {
    gameLog.logGameCreated(event);

    return effects().done();
  }

  Effect onEvent(DotGame.Event.MoveMade event) {
    var lastPlayerId = event.moveHistory().get(event.moveHistory().size() - 1).playerId();
    var lastPlayerStatus = event.player1Status().player().id().equals(lastPlayerId)
        ? event.player1Status()
        : event.player2Status();
    if (lastPlayerStatus.player().isHuman()) {
      gameLog.logLastMove(event);
    }

    return effects().done();
  }

  Effect onEvent(DotGame.Event.MoveForfeited event) {
    gameLog.logForfeitMove(event);

    return effects().done();
  }

  Effect onEvent(DotGame.Event.GameFinished event) {
    gameLog.logGameFinished(event);

    return effects().done();
  }

  Effect onEvent(DotGame.Event.GameCanceled event) {
    gameLog.logGameCanceled(event);

    return effects().done();
  }

  Effect onEventOLD(DotGame.Event.GameCreated event) {
    var currentPlayer = event.currentPlayerStatus();

    if (currentPlayer.isPresent() && currentPlayer.get().player().isAgent()) {
      var agentPlayer = currentPlayer.get();
      var sessionId = AgentPlayer.sessionId(event.gameId(), agentPlayer.player().id());

      componentClient
          .forWorkflow(sessionId)
          .method(AgentPlayerWorkflow::gameCreated)
          .invoke(event);
    }

    return effects().done();
  }

  Effect onEventOLD(DotGame.Event.MoveMade event) {
    var currentPlayer = event.currentPlayerStatus();

    if (currentPlayer.isPresent() && currentPlayer.get().player().isAgent()) {
      var agentPlayer = currentPlayer.get();
      var sessionId = AgentPlayer.sessionId(event.gameId(), agentPlayer.player().id());
      componentClient
          .forWorkflow(sessionId)
          .method(AgentPlayerWorkflow::moveMade)
          .invoke(event);
    } else {
      gameLog.logLastMove(event);
    }

    return effects().done();
  }

  Effect onEventOLD(DotGame.Event.MoveForfeited event) {
    var currentPlayer = event.currentPlayerStatus();

    if (currentPlayer.isPresent() && currentPlayer.get().player().isAgent()) {
      var agentPlayer = currentPlayer.get();
      var sessionId = AgentPlayer.sessionId(event.gameId(), agentPlayer.player().id());
      componentClient
          .forWorkflow(sessionId)
          .method(AgentPlayerWorkflow::moveForfeited)
          .invoke(event);
    }

    return effects().done();
  }

  Effect onEventOLD(DotGame.Event.GameFinished event) {
    var state = componentClient
        .forEventSourcedEntity(event.gameId())
        .method(DotGameEntity::getState)
        .invoke();

    if (state.player1Status().player().isAgent()) {
      var sessionId = AgentPlayer.sessionId(event.gameId(), state.player1Status().player().id());

      componentClient
          .forWorkflow(sessionId)
          .method(AgentPlayerWorkflow::postGameReview)
          .invoke(event);
    }

    if (state.player2Status().player().isAgent()) {
      var sessionId = AgentPlayer.sessionId(event.gameId(), state.player2Status().player().id());

      componentClient
          .forWorkflow(sessionId)
          .method(AgentPlayerWorkflow::postGameReview)
          .invoke(event);
    }

    return effects().done();
  }

  Effect onEventOLD(DotGame.Event.GameCanceled event) {
    var state = componentClient
        .forEventSourcedEntity(event.gameId())
        .method(DotGameEntity::getState)
        .invoke();

    if (state.player1Status().player().isAgent()) {
      var sessionId = AgentPlayer.sessionId(event.gameId(), state.player1Status().player().id());

      componentClient
          .forWorkflow(sessionId)
          .method(AgentPlayerWorkflow::canceledGameReview)
          .invoke(event);
    }

    if (state.player2Status().player().isAgent()) {
      var sessionId = AgentPlayer.sessionId(event.gameId(), state.player2Status().player().id());

      componentClient
          .forWorkflow(sessionId)
          .method(AgentPlayerWorkflow::canceledGameReview)
          .invoke(event);
    }

    return effects().done();
  }
}
