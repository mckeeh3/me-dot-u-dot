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

  @Query("""
      SELECT *
        FROM dot_game_view
       WHERE gameId = :gameId
       LIMIT 1
      """)
  public QueryStreamEffect<DotGameRow> getGameById(GetGameByIdRequest request) {
    return queryStreamResult();
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
        case DotGame.Event.GameCompleted e -> effects().updateRow(onEvent(e));
      };
    }

    private DotGameRow onEvent(DotGame.Event.GameCreated event) {
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
          Optional.empty());
    }

    private DotGameRow onEvent(DotGame.Event.MoveMade event) {
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
          winnerId);
    }

    private DotGameRow onEvent(DotGame.Event.GameCompleted e) {
      var p1 = e.player1Status();
      var p2 = e.player2Status();
      var winnerId = e.winningPlayerStatus().map(ps -> ps.player().id());

      return new DotGameRow(
          e.gameId(),
          rowState().createdAt(),
          e.timestamp(),
          e.status().toString(),
          rowState().level(),
          p1.player().id(),
          p1.player().name(),
          p1.moves(),
          p1.score(),
          p1.isWinner(),
          p2.player().id(),
          p2.player().name(),
          p2.moves(),
          p2.score(),
          p2.isWinner(),
          "",
          "",
          winnerId);
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
      Optional<String> winnerId) {}

  public record GetGameByIdRequest(String gameId) {}

  public record GetGamesByPlayerPagedRequest(String playerId, int limit, int offset) {}

  public record GamesPage(List<DotGameRow> games) {}
}
