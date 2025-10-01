package com.example.application;

import com.example.domain.GameActionLog;

import java.time.Instant;

import com.example.domain.DotGame;

import akka.javasdk.client.ComponentClient;

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

  public void logMove(DotGame.Event.MoveMade event) {
    var lastMove = event.moveHistory().get(event.moveHistory().size() - 1);
    var playerId = lastMove.playerId();
    var squareId = lastMove.squareId();
    var time = event.updatedAt();
    var message = "Move to square %s made by %s".formatted(squareId, playerId);

    logMove(time, event.gameId(), playerId, message);
  }

  public void logMove(Instant time, String gameId, String playerId, String message) {
    log(GameActionLog.Type.make_move, playerId, gameId, message);
  }

  public void logToolCall(String gameId, String playerId, String toolName, String message) {
    log(GameActionLog.Type.tool_call, playerId, gameId, toolName + ": " + message);
  }

  public void logAgentResponse(String gameId, String playerId, String message) {
    log(GameActionLog.Type.agent_response, playerId, gameId, message);
  }

  public void logForfeitMove(String gameId, String playerId, String message) {
    log(GameActionLog.Type.forfeit_move, playerId, gameId, message);
  }
}
