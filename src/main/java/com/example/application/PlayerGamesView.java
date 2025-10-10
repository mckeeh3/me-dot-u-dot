package com.example.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.PlayerGames;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

@Component(id = "player-games-view")
public class PlayerGamesView extends View {
  static final Logger log = LoggerFactory.getLogger(PlayerGamesView.class);

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
      log.debug("Event: {}", event);

      return switch (event) {
        case PlayerGames.Event.GameAdded e -> {
          if (e.parentBranchId().isPresent()) { // must be the trunk branch
            yield effects().ignore();
          }

          yield effects().updateRow(onEvent(e));
        }
        default -> effects().ignore();
      };
    }

    public PlayerGamesRow onEvent(PlayerGames.Event.GameAdded event) {
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
