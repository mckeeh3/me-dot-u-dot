package com.example.application;

import com.example.domain.GameActionLog;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.time.Instant;

import com.example.domain.DotGame;

import akka.javasdk.client.ComponentClient;
import akka.javasdk.JsonSupport;

public class GameActionLogger {
  final ComponentClient componentClient;

  public GameActionLogger(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public void log(GameActionLog.Type type, String agentId, String gameId, String message) {
    var command = GameActionLog.State.log(type, agentId, gameId, message);

    componentClient
        .forKeyValueEntity(command.id())
        .method(GameActionLogEntity::createAgentLog)
        .invoke(command);
  }

  public void log(GameActionLog.Type type, Instant time, String agentId, String gameId, String message) {
    var command = GameActionLog.State.log(type, time, agentId, gameId, message);

    componentClient
        .forKeyValueEntity(command.id())
        .method(GameActionLogEntity::createAgentLog)
        .invoke(command);
  }

  public void logLastMove(DotGame.Event.MoveMade event) {
    var lastMove = event.moveHistory().get(event.moveHistory().size() - 1);
    var playerId = lastMove.playerId();
    var squareId = lastMove.squareId();
    var time = event.updatedAt();

    var isPlayer1ThisPlayer = playerId.equals(event.player1Status().player().id());
    var thisPlayerScoringMovesJson = isPlayer1ThisPlayer
        ? json(event.player1Status().scoringMoves())
        : json(event.player2Status().scoringMoves());
    var otherPlayerScoringMovesJson = isPlayer1ThisPlayer
        ? json(event.player2Status().scoringMoves())
        : json(event.player1Status().scoringMoves());

    var message = """
        Move to square %s made by %s

        Game status: %s

        This player scoring moves:\n%s

        Other player scoring moves:\n%s
        """.formatted(squareId, playerId, event.status().name(), thisPlayerScoringMovesJson, otherPlayerScoringMovesJson);

    logLastMove(time, event.gameId(), playerId, message);
  }

  public void logForfeitMove(DotGame.Event.MoveForfeited event) {
    var state = componentClient
        .forEventSourcedEntity(event.gameId())
        .method(DotGameEntity::getState)
        .invoke();

    var player1id = state.player1Status().player().id();
    var player2id = state.player2Status().player().id();
    var currentPlayerId = state.currentPlayerStatus().isPresent() ? state.currentPlayerStatus().get().player().id() : "";

    var time = event.updatedAt();
    var playerId = currentPlayerId.equals(player1id) ? player2id : player1id; // current player is NOT the one who forfeited the move
    logForfeitMove(time, event.gameId(), playerId, event.message());
  }

  public void logGameFinished(String agentId, DotGame.Event.GameFinished event) {
    var state = componentClient
        .forEventSourcedEntity(event.gameId())
        .method(DotGameEntity::getState)
        .invoke();

    var time = event.updatedAt();
    var player1Id = state.player1Status().player().id();
    var player2Id = state.player2Status().player().id();
    var isAgentWinner = state.player1Status().isWinner() && player1Id.equals(agentId)
        || state.player2Status().isWinner() && player2Id.equals(agentId);
    var message = "Game finished, you " + (isAgentWinner ? "won" : "lost");
    logGameFinished(time, event.gameId(), agentId, message);
  }

  public void logGameCanceled(String agentId, DotGame.Event.GameCanceled event) {
    var time = event.updatedAt();
    var message = "Game canceled";
    logGameCanceled(time, event.gameId(), agentId, message);
  }

  public void logGameCanceled(Instant time, String gameId, String playerId, String message) {
    log(GameActionLog.Type.game_canceled, time, playerId, gameId, message);
  }

  public void logLastMove(Instant time, String gameId, String playerId, String message) {
    log(GameActionLog.Type.make_move, time, playerId, gameId, message);
  }

  public void logToolCall(String gameId, String playerId, String toolName, String message) {
    log(GameActionLog.Type.tool_call, playerId, gameId, toolName + ": " + message);
  }

  public void logModelPrompt(String gameId, String playerId, String message) {
    log(GameActionLog.Type.model_prompt, playerId, gameId, message);
  }

  public void logModelResponse(String gameId, String playerId, String message) {
    log(GameActionLog.Type.model_response, playerId, gameId, message);
  }

  public void logForfeitMove(Instant time, String gameId, String playerId, String message) {
    log(GameActionLog.Type.forfeit_move, time, playerId, gameId, message);
  }

  public void logGameFinished(Instant time, String gameId, String playerId, String message) {
    log(GameActionLog.Type.game_finished, time, playerId, gameId, message);
  }

  public void logGuardrailEvent(String gameId, String playerId, String message) {
    log(GameActionLog.Type.guardrail_event, playerId, gameId, message);
  }

  String json(DotGame.ScoringMoves scoringMoves) {
    var om = JsonSupport.getObjectMapper();
    try {
      return om.writerWithDefaultPrettyPrinter().writeValueAsString(scoringMoves);
    } catch (JsonProcessingException e) {
      return "Get scoring moves failed: %s".formatted(e.getMessage());
    }
  }
}
