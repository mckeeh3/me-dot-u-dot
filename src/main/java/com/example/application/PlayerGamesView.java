package com.example.application;

import java.util.List;

import com.example.domain.PlayerGames;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

@ComponentId("player-games-view")
public class PlayerGamesView extends View {

  @Query("""
      SELECT * AS playerGames
        FROM player_games_view
       ORDER BY gamesWon DESC
       LIMIT :limit OFFSET :offset
      """)
  public QueryEffect<LeaderBoard> getLeaderBoard(GetLeaderBoard request) {
    return queryResult();
  }

  @Consume.FromEventSourcedEntity(PlayerGamesEntity.class)
  public static class ByPlayer extends TableUpdater<PlayerGamesRow> {

    public Effect<PlayerGamesRow> onEvent(PlayerGames.Event event) {
      return switch (event) {
        case PlayerGames.Event.GameAdded e -> effects().updateRow(onEvent(e));
        default -> effects().ignore();
      };
    }

    public PlayerGamesRow onEvent(PlayerGames.Event.GameAdded event) {
      if (event.parentBranchId().isPresent()) { // must be the trunk branch
        return rowState();
      }

      var stats = PlayerGames.State.reduceStats(event.subBranches(), event.leaves());

      return new PlayerGamesRow(
          event.playerId(),
          stats.gamesPlayed(),
          stats.gamesWon(),
          stats.gamesLost(),
          stats.gamesDraw());
    }
  }

  public record PlayerGamesRow(
      String playerId,
      int gamesPlayed,
      int gamesWon,
      int gamesLost,
      int gamesDraw) {}

  public record GetLeaderBoard(int limit, int offset) {}

  public record LeaderBoard(List<PlayerGamesRow> playerGames) {}
}
