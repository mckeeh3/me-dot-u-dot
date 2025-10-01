package com.example.application;

import java.time.Instant;
import java.util.List;

import com.example.domain.GameActionLog;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

@ComponentId("agent-log-view")
public class GameActionLogView extends View {

  @Query("""
      SELECT * AS logs, next_page_token() as nextPageToken, has_more() as hasMore
        FROM agent_log_view
       WHERE gameId = :gameId
       ORDER BY logTime DESC
       LIMIT :limit
       OFFSET page_token_offset(:nextPageToken)
      """)
  public QueryEffect<Logs> getLogsByGame(GetLogsByGameRequest request) {
    return queryResult();
  }

  @Consume.FromKeyValueEntity(GameActionLogEntity.class)
  public static class FromAgentLogEntity extends TableUpdater<LogRow> {
    public Effect<LogRow> onChange(GameActionLog.State state) {

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
      String agentId,
      String gameId,
      String message) {}

  public record Logs(List<LogRow> logs, String nextPageToken, boolean hasMore) {}

  public record GetLogsByGameRequest(String gameId, long limit, String nextPageToken) {}
}
