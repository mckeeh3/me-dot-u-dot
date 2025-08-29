package com.example.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.example.domain.DotGame;
import com.example.domain.PlayerGames;
import com.example.domain.PlayerGames.GameStats;

import akka.javasdk.testkit.TestKitSupport;

public class PlayerGamesIntegrationTest extends TestKitSupport {

  @Test
  @Disabled
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
  @Disabled
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

  @Test
  void testPlayGame() throws InterruptedException {
    var player1Id = "player3";
    var player2Id = "player4";
    var gameId = "game1";
    var level = DotGame.Board.Level.one;

    var playerType = DotGame.PlayerType.human;
    var player1 = new DotGame.Player(player1Id, playerType, "player1", "model1");
    var player2 = new DotGame.Player(player2Id, playerType, "player2", "model2");

    var command = new DotGame.Command.CreateGame(gameId, player1, player2, level);
    componentClient.forEventSourcedEntity(gameId)
        .method(DotGameEntity::createGame)
        .invoke(command);

    makeMove(gameId, player1Id, "A1");
    makeMove(gameId, player2Id, "B1");
    makeMove(gameId, player1Id, "A2");
    makeMove(gameId, player2Id, "B2");
    makeMove(gameId, player1Id, "A3");
    makeMove(gameId, player2Id, "B3");
    makeMove(gameId, player1Id, "A4");
    makeMove(gameId, player2Id, "B4");
    makeMove(gameId, player1Id, "A5"); // player1 winning move

    Thread.sleep(10_000); // allow time for the events to be processed by consumers

    var playerGamesState1 = componentClient.forEventSourcedEntity(player1Id)
        .method(PlayerGamesEntity::getState)
        .invoke();

    var playerGamesState2 = componentClient.forEventSourcedEntity(player2Id)
        .method(PlayerGamesEntity::getState)
        .invoke();

    assertEquals(1, playerGamesState1.reduceStats().gamesPlayed());
    assertEquals(1, playerGamesState1.reduceStats().gamesWon());
    assertEquals(0, playerGamesState1.reduceStats().gamesLost());
    assertEquals(0, playerGamesState1.reduceStats().gamesDraw());

    assertEquals(1, playerGamesState2.reduceStats().gamesPlayed());
    assertEquals(0, playerGamesState2.reduceStats().gamesWon());
    assertEquals(1, playerGamesState2.reduceStats().gamesLost());
    assertEquals(0, playerGamesState2.reduceStats().gamesDraw());
  }

  void makeMove(String gameId, String playerId, String dotId) {
    var command = new DotGame.Command.MakeMove(gameId, playerId, dotId);
    componentClient.forEventSourcedEntity(gameId)
        .method(DotGameEntity::makeMove)
        .invoke(command);
  }
}
