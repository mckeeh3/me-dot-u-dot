package com.example.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.example.domain.DotGame;

import akka.javasdk.testkit.EventSourcedResult;
import akka.javasdk.testkit.EventSourcedTestKit;

public class DotGameEntityTest {

  @Test
  void testCreateGame() {
    var testKit = EventSourcedTestKit.of(DotGameEntity::new);
    var gameId = "game-123";
    var player1 = new DotGame.Player("player1", DotGame.PlayerType.human, "Alice");
    var player2 = new DotGame.Player("player2", DotGame.PlayerType.human, "Bob");
    var player1Status = new DotGame.PlayerStatus(player1, 0, 0, false);
    var player2Status = new DotGame.PlayerStatus(player2, 0, 0, false);
    var command = new DotGame.Command.CreateGame(gameId, player1, player2, DotGame.Board.Level.one);

    var result = testKit.method(DotGameEntity::createGame).invoke(command);

    assertTrue(result.isReply());
    assertEquals(testKit.getState(), result.getReply());

    var event = result.getNextEventOfType(DotGame.Event.GameCreated.class);
    assertEquals(gameId, event.gameId());
    assertEquals(DotGame.Status.in_progress, event.status());
    assertEquals(player1Status, event.player1Status());
    assertEquals(player2Status, event.player2Status());
    assertEquals(player1Status, event.currentPlayerStatus().get());
    assertEquals(DotGame.Board.Level.one, event.level());

    var state = testKit.getState();
    assertEquals(gameId, state.gameId());
    assertEquals(DotGame.Status.in_progress, state.status());
    assertEquals(player1Status, state.player1Status());
    assertEquals(player2Status, state.player2Status());
    assertTrue(state.currentPlayer().isPresent());
    assertEquals(player1Status, state.currentPlayer().get());
    assertTrue(state.moveHistory().isEmpty());

    // Verify the board was properly initialized
    assertEquals(25, state.board().dots().size()); // 5x5 board
  }

  @Test
  void testMakeMove() {
    var testKit = EventSourcedTestKit.of(DotGameEntity::new);
    var gameId = "game-456";
    var player1 = new DotGame.Player("player1", DotGame.PlayerType.human, "Alice");
    var player2 = new DotGame.Player("player2", DotGame.PlayerType.human, "Bob");
    var player2Status = new DotGame.PlayerStatus(player2, 0, 0, false);

    // First create the game
    createGame(testKit, gameId, player1, player2, DotGame.Board.Level.one);

    // Then make a move
    var dotId = "C3";
    var result = makeMove(testKit, gameId, "player1", dotId);

    assertTrue(result.isReply());
    assertEquals(testKit.getState(), result.getReply());

    var event = result.getNextEventOfType(DotGame.Event.MoveMade.class);
    assertEquals(gameId, event.gameId());
    assertEquals(DotGame.Status.in_progress, event.status());
    assertTrue(event.currentPlayerStatus().isPresent());
    assertEquals(player2Status, event.currentPlayerStatus().get()); // Should be player2's turn
    assertEquals(1, event.moveHistory().size());

    // Verify the dot was placed in the board
    var placedDot = event.board().dotAt(dotId);
    assertTrue(placedDot.isPresent());
    assertEquals(player1, placedDot.get().player().get());

    var state = testKit.getState();
    assertEquals(gameId, state.gameId());
    assertEquals(DotGame.Status.in_progress, state.status());
    assertTrue(state.currentPlayer().isPresent());
    assertEquals(player2Status, state.currentPlayer().get()); // Should be player2's turn
    assertEquals(1, state.moveHistory().size());

    // Verify the dot was placed
    var statePlacedDot = state.board().dotAt(dotId);
    assertTrue(statePlacedDot.isPresent());
    assertEquals(player1, statePlacedDot.get().player().get());
  }

  @Test
  void testMakeMoveOnEmptyGame() {
    var testKit = EventSourcedTestKit.of(DotGameEntity::new);
    var gameId = "game-789";
    var dotId = "C3";
    var result = makeMove(testKit, gameId, "player1", dotId);

    assertTrue(result.isReply());
    assertEquals(testKit.getState(), result.getReply());

    // Should not emit any events since the game doesn't exist
    assertEquals(0, result.getAllEvents().size());
  }

  @Test
  void testMakeMoveOutOfTurn() {
    var testKit = EventSourcedTestKit.of(DotGameEntity::new);
    var gameId = "game-101";
    var player1 = new DotGame.Player("player1", DotGame.PlayerType.human, "Alice");
    var player2 = new DotGame.Player("player2", DotGame.PlayerType.human, "Bob");

    // First create the game
    createGame(testKit, gameId, player1, player2, DotGame.Board.Level.one);

    // Player2 tries to move out of turn
    var dotId = "C3";
    var result = makeMove(testKit, gameId, "player2", dotId);

    assertTrue(result.isReply());
    assertEquals(testKit.getState(), result.getReply());

    // Should not emit any events since it's not player2's turn
    assertEquals(0, result.getAllEvents().size());
  }

  @Test
  void testMakeMoveOnOccupiedDot() {
    var testKit = EventSourcedTestKit.of(DotGameEntity::new);
    var gameId = "game-202";
    var player1 = new DotGame.Player("player1", DotGame.PlayerType.human, "Alice");
    var player2 = new DotGame.Player("player2", DotGame.PlayerType.human, "Bob");

    // First create the game
    createGame(testKit, gameId, player1, player2, DotGame.Board.Level.one);

    // Player1 makes first move
    var dotId = "C3";
    makeMove(testKit, gameId, "player1", dotId);

    // Player2 tries to move on the same dot
    var result = makeMove(testKit, gameId, "player2", dotId);

    assertTrue(result.isReply());
    assertEquals(testKit.getState(), result.getReply());

    // Should not emit any events since the dot is already occupied
    assertEquals(1, result.getAllEvents().size());
    var event = result.getNextEventOfType(DotGame.Event.MoveForfeited.class);
    assertEquals(gameId, event.gameId());
    assertTrue(event.currentPlayer().isPresent());
    assertEquals(player1, event.currentPlayer().get().player());
  }

  @Test
  void testMakeMoveOnInvalidCoordinates() {
    var testKit = EventSourcedTestKit.of(DotGameEntity::new);
    var gameId = "game-303";
    var player1 = new DotGame.Player("player1", DotGame.PlayerType.human, "Alice");
    var player2 = new DotGame.Player("player2", DotGame.PlayerType.human, "Bob");

    // First create the game
    createGame(testKit, gameId, player1, player2, DotGame.Board.Level.one);

    // Player1 tries to move on invalid coordinates
    var invalidDotId = "Z99";
    var result = makeMove(testKit, gameId, "player1", invalidDotId);

    assertTrue(result.isReply());
    assertEquals(testKit.getState(), result.getReply());

    // Should not emit any events since the coordinates are invalid
    assertEquals(1, result.getAllEvents().size());
    var event = result.getNextEventOfType(DotGame.Event.MoveForfeited.class);
    assertEquals(gameId, event.gameId());
    assertTrue(event.currentPlayer().isPresent());
    assertEquals(player2, event.currentPlayer().get().player());
  }

  @Test
  void testMultipleMoves() {
    var testKit = EventSourcedTestKit.of(DotGameEntity::new);
    var gameId = "game-404";
    var player1 = new DotGame.Player("player1", DotGame.PlayerType.human, "Alice");
    var player2 = new DotGame.Player("player2", DotGame.PlayerType.human, "Bob");

    // First create the game
    createGame(testKit, gameId, player1, player2, DotGame.Board.Level.one);

    // Player1 makes first move
    makeMove(testKit, gameId, "player1", "C3");

    // Player2 makes second move
    makeMove(testKit, gameId, "player2", "D3");

    // Player1 makes third move
    var result = makeMove(testKit, gameId, "player1", "C4");

    assertTrue(result.isReply());
    assertEquals(testKit.getState(), result.getReply());

    var state = testKit.getState();
    assertEquals(gameId, state.gameId());
    assertEquals(DotGame.Status.in_progress, state.status());
    assertEquals(3, state.moveHistory().size());

    // Verify all dots were placed correctly
    assertTrue(state.board().dotAt("C3").isPresent());
    assertEquals(player1, state.board().dotAt("C3").get().player().get());

    assertTrue(state.board().dotAt("D3").isPresent());
    assertEquals(player2, state.board().dotAt("D3").get().player().get());

    assertTrue(state.board().dotAt("C4").isPresent());
    assertEquals(player1, state.board().dotAt("C4").get().player().get());
  }

  @Test
  void testGameStateAfterMoves() {
    var testKit = EventSourcedTestKit.of(DotGameEntity::new);
    var gameId = "game-505";
    var player1 = new DotGame.Player("player1", DotGame.PlayerType.human, "Alice");
    var player2 = new DotGame.Player("player2", DotGame.PlayerType.human, "Bob");

    // First create the game
    createGame(testKit, gameId, player1, player2, DotGame.Board.Level.one);

    // Make several moves to test state consistency
    makeMove(testKit, gameId, "player1", "C3");
    makeMove(testKit, gameId, "player2", "D3");
    makeMove(testKit, gameId, "player1", "C4");
    makeMove(testKit, gameId, "player2", "D4");

    var state = testKit.getState();

    // Verify game state
    assertEquals(gameId, state.gameId());
    assertEquals(DotGame.Status.in_progress, state.status());
    assertEquals(4, state.moveHistory().size());

    // Verify board state
    var board = state.board();
    assertTrue(board.dotAt("C3").isPresent());
    assertTrue(board.dotAt("D3").isPresent());
    assertTrue(board.dotAt("C4").isPresent());
    assertTrue(board.dotAt("D4").isPresent());

    // Verify current player (should be player1's turn after 4 moves)
    assertTrue(state.currentPlayer().isPresent());
    assertEquals(player1, state.currentPlayer().get().player());
  }

  @Test
  void testCreateGameWithSameId() {
    var testKit = EventSourcedTestKit.of(DotGameEntity::new);
    var gameId = "game-606";
    var player1 = new DotGame.Player("player1", DotGame.PlayerType.human, "Alice");
    var player2 = new DotGame.Player("player2", DotGame.PlayerType.human, "Bob");
    var player1Status = new DotGame.PlayerStatus(player1, 0, 0, false);
    var player2Status = new DotGame.PlayerStatus(player2, 0, 0, false);

    // First create the game
    createGame(testKit, gameId, player1, player2, DotGame.Board.Level.one);

    // Try to create another game with the same ID
    var duplicateCommand = new DotGame.Command.CreateGame(gameId, player2, player1, DotGame.Board.Level.one);
    var result = testKit.method(DotGameEntity::createGame).invoke(duplicateCommand);

    assertTrue(result.isReply());
    assertEquals(testKit.getState(), result.getReply());

    // Should not emit any events since the game already exists
    assertEquals(0, result.getAllEvents().size());

    // State should remain unchanged
    var state = testKit.getState();
    assertEquals(player1Status, state.player1Status());
    assertEquals(player2Status, state.player2Status());
  }

  @Test
  void testPlayer2ScoresOnePointFromCenterVerticalUp() {
    var testKit = EventSourcedTestKit.of(DotGameEntity::new);
    var gameId = "game-707";
    var player1 = new DotGame.Player("player1", DotGame.PlayerType.human, "Alice");
    var player2 = new DotGame.Player("player2", DotGame.PlayerType.human, "Bob");

    // First create the game
    createGame(testKit, gameId, player1, player2, DotGame.Board.Level.one);

    // Player1 C3->C2->C1
    // Player2 D3->D2
    makeMove(testKit, gameId, "player1", "C3");
    makeMove(testKit, gameId, "player2", "D3");
    makeMove(testKit, gameId, "player1", "C2");
    makeMove(testKit, gameId, "player2", "D2");
    makeMove(testKit, gameId, "player1", "C1");

    var state = testKit.getState();

    // C3 should score 1 point because it has a vertical line down to C1 (length 3)
    // Required length for level 1: (5/2) + 1 = 3
    assertEquals(1, state.board().scoreDotAt("C3"));

    // C1 should score 1 point because it has a vertical line up to C3 (length 3)
    assertEquals(1, state.board().scoreDotAt("C1"));

    // C2 should score 1 points because it's in the middle of line of length 3
    assertEquals(1, state.board().scoreDotAt("C2"));
  }

  @Test
  void testPlayer2ScoresOnePointWithDiagonalLineFromG3ToC7LevelTwo() {
    var testKit = EventSourcedTestKit.of(DotGameEntity::new);
    var gameId = "game-808";
    var player1 = new DotGame.Player("player1", DotGame.PlayerType.human, "Alice");
    var player2 = new DotGame.Player("player2", DotGame.PlayerType.human, "Bob");

    createGame(testKit, gameId, player1, player2, DotGame.Board.Level.two);

    // player1 A1->A2->A3->A4->A5
    // player2 G3->F4->E5->D6->C7
    makeMove(testKit, gameId, "player1", "A1");
    makeMove(testKit, gameId, "player2", "G3");
    makeMove(testKit, gameId, "player1", "A2");
    makeMove(testKit, gameId, "player2", "F4");
    makeMove(testKit, gameId, "player1", "A3");
    makeMove(testKit, gameId, "player2", "E5");
    makeMove(testKit, gameId, "player1", "A4"); // First line of player1
    makeMove(testKit, gameId, "player2", "D6"); // First line of player2
    makeMove(testKit, gameId, "player1", "A5"); // Bonus point for player1
    makeMove(testKit, gameId, "player2", "C7"); // Bonus point for player2

    var state = testKit.getState();
    assertEquals(1, state.board().scoreDotAt("G3"));
    assertEquals(1, state.board().scoreDotAt("F4"));
    assertEquals(1, state.board().scoreDotAt("E5"));
    assertEquals(1, state.board().scoreDotAt("D6"));
    assertEquals(1, state.board().scoreDotAt("C7"));
    assertEquals(0, state.board().scoreDotAt("D8")); // D8 is not in a line

    assertEquals(1, state.board().scoreDotAt("A1"));
    assertEquals(1, state.board().scoreDotAt("A2"));
    assertEquals(1, state.board().scoreDotAt("A3"));
    assertEquals(1, state.board().scoreDotAt("A4"));
    assertEquals(1, state.board().scoreDotAt("A5"));
    assertEquals(0, state.board().scoreDotAt("A6")); // A6 is not in a line

    assertEquals(2, state.player1Status().score());
    assertEquals(2, state.player2Status().score());
  }

  @Test
  void testGameCompletedPlayer1Wins() {
    var testKit = EventSourcedTestKit.of(DotGameEntity::new);
    var gameId = "game-909";
    var player1 = new DotGame.Player("player1", DotGame.PlayerType.human, "Alice");
    var player2 = new DotGame.Player("player2", DotGame.PlayerType.human, "Bob");

    createGame(testKit, gameId, player1, player2, DotGame.Board.Level.one);

    makeMove(testKit, gameId, "player1", "A1");
    makeMove(testKit, gameId, "player2", "B1");
    makeMove(testKit, gameId, "player1", "A2");
    makeMove(testKit, gameId, "player2", "B2");
    makeMove(testKit, gameId, "player1", "A3");
    makeMove(testKit, gameId, "player2", "B3");
    makeMove(testKit, gameId, "player1", "A4");
    makeMove(testKit, gameId, "player2", "B4");

    var result = makeMove(testKit, gameId, "player1", "A5"); // Player1 wins
    assertTrue(result.isReply());
    assertEquals(DotGame.Status.won_by_player, result.getReply().status());

    assertEquals(1, result.getAllEvents().size());

    var event = result.getNextEventOfType(DotGame.Event.MoveMade.class);
    assertEquals(gameId, event.gameId());
    assertEquals(DotGame.Status.won_by_player, event.status());
    assertEquals(9, event.moveHistory().size());
    assertEquals(new DotGame.Move("A5", "player1"), event.moveHistory().get(event.moveHistory().size() - 1));

    var state = testKit.getState();

    assertEquals(DotGame.Status.won_by_player, state.status());
    assertEquals(player1, state.player1Status().player());
    assertEquals(player2, state.player2Status().player());
    assertTrue(state.currentPlayer().isEmpty());
    assertTrue(state.player1Status().isWinner());
    assertFalse(state.player2Status().isWinner());
  }

  @Test
  void testGameCompletedPlayer2Wins() {
    var testKit = EventSourcedTestKit.of(DotGameEntity::new);
    var gameId = "game-909";
    var player1 = new DotGame.Player("player1", DotGame.PlayerType.human, "Alice");
    var player2 = new DotGame.Player("player2", DotGame.PlayerType.human, "Bob");

    createGame(testKit, gameId, player1, player2, DotGame.Board.Level.one);

    makeMove(testKit, gameId, "player1", "A1");
    makeMove(testKit, gameId, "player2", "B1");
    makeMove(testKit, gameId, "player1", "A2");
    makeMove(testKit, gameId, "player2", "B2");
    makeMove(testKit, gameId, "player1", "A3");
    makeMove(testKit, gameId, "player2", "B3");
    makeMove(testKit, gameId, "player1", "C4");
    makeMove(testKit, gameId, "player2", "B4");
    makeMove(testKit, gameId, "player1", "C5");

    var result = makeMove(testKit, gameId, "player2", "B5"); // Player2 wins
    assertTrue(result.isReply());
    assertEquals(DotGame.Status.won_by_player, result.getReply().status());

    assertEquals(1, result.getAllEvents().size());

    var event = result.getNextEventOfType(DotGame.Event.MoveMade.class);
    assertEquals(gameId, event.gameId());
    assertEquals(DotGame.Status.won_by_player, event.status());
    assertEquals(10, event.moveHistory().size());
    assertEquals(new DotGame.Move("B5", "player2"), event.moveHistory().get(event.moveHistory().size() - 1));

    var state = testKit.getState();

    assertEquals(DotGame.Status.won_by_player, state.status());
    assertEquals(player1, state.player1Status().player());
    assertEquals(player2, state.player2Status().player());
    assertTrue(state.currentPlayer().isEmpty());
    assertFalse(state.player1Status().isWinner());
    assertTrue(state.player2Status().isWinner());
  }

  static EventSourcedResult<DotGame.State> createGame(EventSourcedTestKit<DotGame.State, DotGame.Event, DotGameEntity> testKit, String gameId, DotGame.Player player1, DotGame.Player player2, DotGame.Board.Level level) {
    var command = new DotGame.Command.CreateGame(gameId, player1, player2, level);
    return testKit.method(DotGameEntity::createGame).invoke(command);
  }

  static EventSourcedResult<DotGame.State> makeMove(EventSourcedTestKit<DotGame.State, DotGame.Event, DotGameEntity> testKit, String gameId, String playerId, String dotId) {
    var command = new DotGame.Command.MakeMove(gameId, playerId, dotId);
    return testKit.method(DotGameEntity::makeMove).invoke(command);
  }
}
