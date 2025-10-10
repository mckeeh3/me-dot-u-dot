package com.example.application;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.GameActionLog;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

@Component(id = "game-action-log-view")
public class GameActionLogView extends View {
  static final Logger log = LoggerFactory.getLogger(GameActionLogView.class);

  @Query("""
      SELECT * AS logs, has_more() as hasMore
        FROM game_action_log_view
       WHERE gameId = :gameId
       ORDER BY time ASC
       LIMIT :limit
       OFFSET :offset
      """)
  public QueryEffect<Logs> getLogsByGame(GetLogsByGameRequest request) {
    return queryResult();
  }

  @Consume.FromKeyValueEntity(GameActionLogEntity.class)
  public static class FromGameActionLogEntity extends TableUpdater<LogRow> {
    public Effect<LogRow> onChange(GameActionLog.State state) {
      if (null == state.gameId()) {
        log.warn("Game ID is null");
        log.warn("State: {}", state);

        return effects().ignore();
      }

      return effects().updateRow(new LogRow(
          state.id(),
          state.type(),
          state.time(),
          state.playerId(),
          state.gameId(),
          state.message()));
    }
  }

  public record LogRow(
      String id,
      GameActionLog.Type type,
      Instant time,
      String playerId,
      String gameId,
      String message) {}

  public record Logs(List<LogRow> logs, boolean hasMore) {}

  public record GetLogsByGameRequest(String gameId, int limit, int offset) {}
}
