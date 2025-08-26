package com.example.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.example.domain.DotGame;

@ComponentId("dot-game-view")
public class DotGameView extends View {

  @Query(value = """
      SELECT *
        FROM dot_game_view
       WHERE gameId = :gameId
       LIMIT 1
      """, streamUpdates = true)
  public QueryStreamEffect<DotGameRow> getMoveStreamByGameId(GetMoveStreamByGameIdRequest request) {
    return queryStreamResult();
  }

  @Query(value = """
      SELECT *
        FROM dot_game_view
       WHERE status = 'in_progress'
       ORDER BY createdAt DESC
       LIMIT 1
      """)
  public QueryEffect<DotGameRow> getCurrentInProgressGame() {
    return queryResult();
  }

  @Query("""
      SELECT * AS games
        FROM dot_game_view
       WHERE player1Id = :playerId OR player2Id = :playerId
       ORDER BY createdAt DESC
       LIMIT :limit OFFSET :offset
      """)
  public QueryEffect<GamesPage> getGamesByPlayerPaged(GetGamesByPlayerPagedRequest request) {
    return queryResult();
  }

  @Consume.FromEventSourcedEntity(DotGameEntity.class)
  public static class ByGameId extends TableUpdater<DotGameRow> {

    public Effect<DotGameRow> onEvent(DotGame.Event event) {
      return switch (event) {
        case DotGame.Event.GameCreated e -> effects().updateRow(onEvent(e));
        case DotGame.Event.MoveMade e -> effects().updateRow(onEvent(e));
        case DotGame.Event.GameCanceled e -> effects().updateRow(onEvent(e));
        case DotGame.Event.MoveForfeited e -> effects().updateRow(onEvent(e));
        case DotGame.Event.GameFinished e -> effects().updateRow(onEvent(e));
      };
    }

    public enum LastAction {
      game_created,
      move_made,
      game_canceled,
      move_forfeited,
      game_finished
    }

    DotGameRow onEvent(DotGame.Event.GameCreated event) {
      var player1 = event.player1Status();
      var player2 = event.player2Status();
      var currentPlayerId = event.currentPlayerStatus().map(ps -> ps.player().id()).orElse("");
      var currentPlayerName = event.currentPlayerStatus().map(ps -> ps.player().name()).orElse("");

      return new DotGameRow(
          event.gameId(),
          event.createdAt(),
          event.createdAt(),
          event.status().toString(),
          event.level().name(),
          player1.player().id(),
          player1.player().name(),
          player1.moves(),
          player1.score(),
          player1.isWinner(),
          player2.player().id(),
          player2.player().name(),
          player2.moves(),
          player2.score(),
          player2.isWinner(),
          currentPlayerId,
          currentPlayerName,
          Optional.empty(),
          LastAction.game_created.name(),
          "");
    }

    DotGameRow onEvent(DotGame.Event.MoveMade event) {
      var player1 = event.player1Status();
      var player2 = event.player2Status();
      var currentPlayerId = event.currentPlayerStatus().map(ps -> ps.player().id()).orElse("");
      var currentPlayerName = event.currentPlayerStatus().map(ps -> ps.player().name()).orElse("");
      var winnerId = player1.isWinner() ? Optional.of(player1.player().id()) : player2.isWinner() ? Optional.of(player2.player().id()) : Optional.<String>empty();

      return new DotGameRow(
          event.gameId(),
          rowState().createdAt(),
          event.timestamp(),
          event.status().toString(),
          rowState().level(),
          player1.player().id(),
          player1.player().name(),
          player1.moves(),
          player1.score(),
          player1.isWinner(),
          player2.player().id(),
          player2.player().name(),
          player2.moves(),
          player2.score(),
          player2.isWinner(),
          currentPlayerId,
          currentPlayerName,
          winnerId,
          LastAction.move_made.name(),
          "");
    }

    DotGameRow onEvent(DotGame.Event.GameCanceled event) {
      return new DotGameRow(
          event.gameId(),
          rowState().createdAt(),
          event.timestamp(),
          event.status().toString(),
          rowState().level(),
          rowState().player1Id(),
          rowState().player1Name(),
          rowState().player1Moves(),
          rowState().player1Score(),
          rowState().player1Winner(),
          rowState().player2Id(),
          rowState().player2Name(),
          rowState().player2Moves(),
          rowState().player2Score(),
          rowState().player2Winner(),
          rowState().currentPlayerId(),
          rowState().currentPlayerName(),
          Optional.empty(),
          LastAction.game_canceled.name(),
          "");
    }

    DotGameRow onEvent(DotGame.Event.MoveForfeited event) {
      var newCurrentPlayerId = event.currentPlayer().map(ps -> ps.player().id()).orElse("");
      var newCurrentPlayerName = event.currentPlayer().map(ps -> ps.player().name()).orElse("");

      return new DotGameRow(
          event.gameId(),
          rowState().createdAt(),
          event.timestamp(),
          rowState().status(),
          rowState().level(),
          rowState().player1Id(),
          rowState().player1Name(),
          rowState().player1Moves(),
          rowState().player1Score(),
          rowState().player1Winner(),
          rowState().player2Id(),
          rowState().player2Name(),
          rowState().player2Moves(),
          rowState().player2Score(),
          rowState().player2Winner(),
          newCurrentPlayerId,
          newCurrentPlayerName,
          Optional.empty(),
          LastAction.move_forfeited.name(),
          event.message());
    }

    DotGameRow onEvent(DotGame.Event.GameFinished event) {
      return new DotGameRow(
          event.gameId(),
          rowState().createdAt(),
          event.finishedAt().orElse(Instant.now()),
          rowState().status(),
          rowState().level(),
          rowState().player1Id(),
          rowState().player1Name(),
          rowState().player1Moves(),
          rowState().player1Score(),
          rowState().player1Winner(),
          rowState().player2Id(),
          rowState().player2Name(),
          rowState().player2Moves(),
          rowState().player2Score(),
          rowState().player2Winner(),
          rowState().currentPlayerId(),
          rowState().currentPlayerName(),
          Optional.empty(),
          LastAction.game_finished.name(),
          "");
    }
  }

  public record DotGameRow(
      String gameId,
      Instant createdAt,
      Instant updatedAt,
      String status,
      String level,
      String player1Id,
      String player1Name,
      int player1Moves,
      int player1Score,
      boolean player1Winner,
      String player2Id,
      String player2Name,
      int player2Moves,
      int player2Score,
      boolean player2Winner,
      String currentPlayerId,
      String currentPlayerName,
      Optional<String> winnerId,
      String lastAction,
      String message) {}

  public record GetMoveStreamByGameIdRequest(String gameId) {}

  public record GetGamesByPlayerPagedRequest(String playerId, int limit, int offset) {}

  public record GamesPage(List<DotGameRow> games) {}
}
