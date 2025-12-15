package com.example.application;

import java.time.Instant;
import java.util.List;

import com.example.domain.GameMoveLog;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

@Component(id = "game-move-log-view")
public class GameMoveLogView extends View {

  @Query("""
      SELECT * AS gameMoveLogs
        FROM game_move_log_view
       WHERE gameId = :gameId
       AND agentId = :agentId
       ORDER BY moveNumber ASC
      """)
  public QueryEffect<GameMoveLogs> getByGameIdAndAgentId(GetByGameIdAndAgentIdRequest request) {
    return queryResult();
  }

  @Consume.FromEventSourcedEntity(GameMoveLogEntity.class)
  public static class ByGameAndAgent extends TableUpdater<GameMoveLogRow> {

    public Effect<GameMoveLogRow> onEvent(GameMoveLog.Event event) {
      return switch (event) {
        case GameMoveLog.Event.GameMoveLogCreated e -> effects().updateRow(onEvent(e));
      };
    }

    private GameMoveLogRow onEvent(GameMoveLog.Event.GameMoveLogCreated e) {
      return new GameMoveLogRow(
          e.gameId(),
          e.agentId(),
          e.moveNumber(),
          e.createdAt(),
          e.response());
    }
  }

  public record GameMoveLogRow(
      String gameId,
      String agentId,
      int moveNumber,
      Instant createdAt,
      String response) {}

  public record GetByGameIdAndAgentIdRequest(String gameId, String agentId) {}

  public record GameMoveLogs(List<GameMoveLogRow> gameMoveLogs) {}
}
