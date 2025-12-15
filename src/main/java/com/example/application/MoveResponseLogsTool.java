package com.example.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

import akka.javasdk.JsonSupport;
import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;

public class MoveResponseLogsTool {
  static final Logger log = LoggerFactory.getLogger(MoveResponseLogsTool.class);
  final ComponentClient componentClient;
  final GameActionLogger gameLog;

  public MoveResponseLogsTool(ComponentClient componentClient) {
    this.componentClient = componentClient;
    this.gameLog = new GameActionLogger(componentClient);
  }

  @FunctionTool(description = """
      Retrieve game move logs for a game. This provides agent move summaries returned after it makes a move.
      This is useful for reviewing the game while in progress or after it is finished.

      - Returns a list of move logs, which is an agent's summary of the last move it made.
      - Logs are ordered chronologically from oldest to newest.
      """)
  public Response getMoveResponseLogs(
      @Description("The ID of the game you want to get move logs for") String gameId,
      @Description("The ID of your player/agent id for this game") String agentId) {
    log.debug("GameId: {}, AgentId: {}, Get move response logs", gameId, agentId);

    var request = new GameMoveLogView.GetByGameIdAndAgentIdRequest(gameId, agentId);
    var logs = componentClient.forView()
        .method(GameMoveLogView::getByGameIdAndAgentId)
        .invoke(request);

    var response = Response.from(gameId, agentId, logs);

    if (!agentId.isEmpty()) {
      gameLog.logToolCall(gameId, agentId, "getMoveResponseLogs", json(response));
    }

    return response;
  }

  static String json(Response response) {
    var om = JsonSupport.getObjectMapper();
    try {
      return om.writerWithDefaultPrettyPrinter().writeValueAsString(response);
    } catch (JsonProcessingException e) {
      return "Get game move logs failed: %s".formatted(e.getMessage());
    }
  }

  public record MoveResponse(int moveNumber, String response) {
    static MoveResponse from(GameMoveLogView.GameMoveLogRow gameMoveLogRow) {
      return new MoveResponse(gameMoveLogRow.moveNumber(), gameMoveLogRow.response());
    }
  }

  public record Response(String gameId, String agentId, List<MoveResponse> moveResponses) {
    static Response from(String gameId, String agentId, GameMoveLogView.GameMoveLogs logs) {
      return new Response(gameId, agentId, logs.gameMoveLogs().stream()
          .map(MoveResponse::from)
          .toList());
    }
  }
}
