package com.example.application;

import java.time.Instant;
import java.util.List;

import com.example.domain.GameLog;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

@Component(id = "make-move-response-view")
public class GameLogView extends View {

  @Query("""
      SELECT * AS responses
        FROM make_move_response_view
       WHERE gameId = :gameId
       AND agentId = :agentId
       ORDER BY moveNumber ASC
      """)
  public QueryEffect<Responses> getByGameIdAndAgentId(GetByGameIdAndAgentIdRequest request) {
    return queryResult();
  }

  @Consume.FromEventSourcedEntity(GameLogEntity.class)
  public static class ByGameAndAgent extends TableUpdater<ResponseRow> {

    public Effect<ResponseRow> onEvent(GameLog.Event event) {
      return switch (event) {
        case GameLog.Event.MakeMoveResponseCreated e -> effects().updateRow(onEvent(e));
      };
    }

    private ResponseRow onEvent(GameLog.Event.MakeMoveResponseCreated e) {
      return new ResponseRow(
          e.gameId(),
          e.agentId(),
          e.moveNumber(),
          e.createdAt(),
          e.response());
    }
  }

  public record ResponseRow(
      String gameId,
      String agentId,
      int moveNumber,
      Instant createdAt,
      String response) {}

  public record GetByGameIdAndAgentIdRequest(String gameId, String agentId) {}

  public record Responses(List<ResponseRow> responses) {}
}
