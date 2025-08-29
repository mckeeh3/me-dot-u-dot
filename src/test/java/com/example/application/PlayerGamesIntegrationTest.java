package com.example.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.example.domain.PlayerGames;
import com.example.domain.PlayerGames.GameStats;

import akka.javasdk.testkit.TestKitSupport;

public class PlayerGamesIntegrationTest extends TestKitSupport {

  @Test
  void testAddGameToBranch() throws InterruptedException {
    var stats = new GameStats(1, 1, 1, 1);
    var playerId = "player1";
    var games = 103; // layer 0: max 10 games, layer 1: max 100 games, total max 110 games in 1st 2 layers

    Stream.iterate(1, i -> i <= games, i -> i + 1)
        .forEach(i -> {
          var command = new PlayerGames.Command.AddGame(playerId, "game" + i, stats);
          componentClient.forEventSourcedEntity(playerId)
              .method(PlayerGamesEntity::addGame)
              .invoke(command);
        });

    Thread.sleep(10_000); // allow time for the events to be processed by consumers

    var state = componentClient.forEventSourcedEntity(playerId)
        .method(PlayerGamesEntity::getState)
        .invoke();

    var branchStats = state.reduceStats();
    assertEquals(games, branchStats.gamesPlayed());
    assertEquals(games, branchStats.gamesWon());
    assertEquals(games, branchStats.gamesLost());
    assertEquals(games, branchStats.gamesDraw());
  }

  @Test
  void testAddTwoHundredGames() throws InterruptedException {
    var stats = new GameStats(1, 1, 1, 1);
    var playerId = "player2";
    var games = 234; // layer 0: max 10 games, layer 1: max 100 games, layer 2: max 1000 games, total max 1110 games in 3 layers

    Stream.iterate(1, i -> i <= games, i -> i + 1)
        .forEach(i -> {
          var command = new PlayerGames.Command.AddGame(playerId, "game" + i, stats);
          componentClient.forEventSourcedEntity(playerId)
              .method(PlayerGamesEntity::addGame)
              .invoke(command);
        });

    Thread.sleep(10_000); // allow time for the events to be processed by consumers

    var state = componentClient.forEventSourcedEntity(playerId)
        .method(PlayerGamesEntity::getState)
        .invoke();

    var branchStats = state.reduceStats();
    assertEquals(games, branchStats.gamesPlayed());
    assertEquals(games, branchStats.gamesWon());
    assertEquals(games, branchStats.gamesLost());
    assertEquals(games, branchStats.gamesDraw());
  }
}
