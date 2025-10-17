package com.example.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.example.domain.DotGame;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

@Component(id = "dot-game-view")
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
       LIMIT :limit
      OFFSET :offset
      """)
  public QueryEffect<GamesPage> getGamesByPlayerIdPaged(GetGamesByPlayerIdPagedRequest request) {
    return queryResult();
  }

  @Query("""
      SELECT * AS games
        FROM dot_game_view
       ORDER BY createdAt DESC
       LIMIT :limit
      OFFSET :offset
      """)
  public QueryEffect<GamesPage> getRecentGames(GetRecentGamesRequest request) {
    return queryResult();
  }

  @Consume.FromEventSourcedEntity(DotGameEntity.class)
  public static class ByGameId extends TableUpdater<DotGameRow> {

    public Effect<DotGameRow> onEvent(DotGame.Event event) {
      return switch (event) {
        case DotGame.Event.GameCreated e -> effects().updateRow(onEvent(e));
        case DotGame.Event.MoveMade e -> effects().updateRow(onEvent(e));
        case DotGame.Event.PlayerTurnCompleted e -> effects().updateRow(onEvent(e));
        case DotGame.Event.GameCanceled e -> effects().updateRow(onEvent(e));
        case DotGame.Event.MoveForfeited e -> effects().updateRow(onEvent(e));
        case DotGame.Event.GameFinished e -> effects().updateRow(onEvent(e));
        case DotGame.Event.GameResults e -> effects().updateRow(onEvent(e));
      };
    }

    public enum LastAction {
      game_created,
      move_made,
      player_turn_completed,
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
          rowState().updatedAt(),
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

    DotGameRow onEvent(DotGame.Event.PlayerTurnCompleted event) {
      return new DotGameRow(
          event.gameId(),
          rowState().createdAt(),
          event.turnCompletedAt(),
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
          rowState().winnerId(),
          LastAction.player_turn_completed.name(),
          "");
    }

    DotGameRow onEvent(DotGame.Event.GameCanceled event) {
      return new DotGameRow(
          event.gameId(),
          rowState().createdAt(),
          event.updatedAt(),
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
      var newCurrentPlayerId = event.currentPlayerStatus().map(ps -> ps.player().id()).orElse("");
      var newCurrentPlayerName = event.currentPlayerStatus().map(ps -> ps.player().name()).orElse("");

      return new DotGameRow(
          event.gameId(),
          rowState().createdAt(),
          event.updatedAt(),
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
          LastAction.game_finished.name(),
          "");
    }

    DotGameRow onEvent(DotGame.Event.GameResults event) {
      return rowState();
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

  public record GetGamesByPlayerIdPagedRequest(String playerId, int limit, int offset) {}

  public record GetRecentGamesRequest(int limit, int offset) {}

  public record GamesPage(List<DotGameRow> games) {}
}
