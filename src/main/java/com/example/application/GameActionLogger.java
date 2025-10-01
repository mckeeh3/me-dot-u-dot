package com.example.application;

import com.example.domain.GameActionLog;

import akka.javasdk.client.ComponentClient;

public class GameActionLogger {
  final ComponentClient componentClient;

  public GameActionLogger(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public void log(GameActionLog.Type type, String agentId, String gameId, String message) {
    var command = GameActionLog.State.log(type, agentId, gameId, message);

    componentClient
        .forKeyValueEntity(gameId)
        .method(GameActionLogEntity::createAgentLog)
        .invoke(command);
  }

  public void logMove(String gameId, String playerId, String message) {
    log(GameActionLog.Type.make_move, playerId, gameId, message);
  }

  public void logToolCall(String gameId, String playerId, String message) {
    log(GameActionLog.Type.tool_call, playerId, gameId, message);
  }

  public void logAgentResponse(String gameId, String playerId, String message) {
    log(GameActionLog.Type.agent_response, playerId, gameId, message);
  }

  public void logForfeitMove(String gameId, String playerId, String message) {
    log(GameActionLog.Type.forfeit_move, playerId, gameId, message);
  }
}
