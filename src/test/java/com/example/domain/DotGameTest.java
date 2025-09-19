package com.example.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DotGameTest {

  DotGame.Board level1Board;
  DotGame.Board level3Board;
  DotGame.Player player1;
  DotGame.Player player2;

  @BeforeEach
  void setUp() {
    level1Board = DotGame.Board.of(DotGame.Board.Level.one); // 5x5
    level3Board = DotGame.Board.of(DotGame.Board.Level.three); // 9x9
    player1 = new DotGame.Player("player1", DotGame.PlayerType.human, "Alice", "model1");
    player2 = new DotGame.Player("player2", DotGame.PlayerType.human, "Bob", "model1");
  }

  @Test
  void testScoreDotAt_Level1_All_Across_Horizontal_Vertical_And_Adjacent() {
    // _____ 1 __ 2 __ 3 __ 4 __ 5
    // A | p2 | __ | p1 | __ | p2 |
    // B | __ | p2 | p1 | p2 | __ |
    // C | p1 | p1 | p1 | p1 | p1 |
    // D | __ | p2 | p1 | p2 | __ |
    // E | p2 | __ | p1 | __ | p2 |
    var moveHistory = moveHistory(player1, player2, List.of(
        "A3", "A1",
        "B3", "A5",
        "D3", "B2",
        "E3", "B4",
        "C1", "D2",
        "C2", "D4",
        "C4", "E1",
        "C5", "E5"));
    var move = new DotGame.Square("C3", player1);

    var scoringMoves = DotGame.ScoringMoves
        .create(player1)
        .scoreMove(move, DotGame.Board.Level.one, moveHistory);

    assertEquals(8, scoringMoves.totalScore());
    assertEquals(3, scoringMoves.scoringMoves().size());

    var horizontalScoringMove = scoringMoves.scoringMoves().get(0);
    assertEquals(3, horizontalScoringMove.score());

    var verticalScoringMove = scoringMoves.scoringMoves().get(1);
    assertEquals(3, verticalScoringMove.score());

    var adjacentScoringMove = scoringMoves.scoringMoves().get(2);
    assertEquals(2, adjacentScoringMove.score());
  }

  @Test
  void testScoringMovesAt_Level1_All_Diagonal_And_adjacent() {
    // _____ 1 __ 2 __ 3 __ 4 __ 5
    // A | p1 | P2 | __ | P2 | p1 |
    // B | __ | p1 | p2 | p1 | P2 |
    // C | P2 | P2 | P1 | P2 | P2 |
    // D | __ | P1 | __ | P1 | __ |
    // E | P1 | __ | __ | __ | P1 |
    var moveHistory = moveHistory(player1, player2, List.of(
        "A1", "A2",
        "A5", "A4",
        "B2", "B3",
        "B4", "B5",
        "D2", "C2",
        "D4", "C4",
        "E1", "C1",
        "E5", "C5"));
    var move = new DotGame.Square("C3", player1);

    var scoringMoves = DotGame.ScoringMoves
        .create(player1)
        .scoreMove(move, DotGame.Board.Level.one, moveHistory);

    assertEquals(8, scoringMoves.totalScore());
    assertEquals(3, scoringMoves.scoringMoves().size());

    var diagonalScoringMove1 = scoringMoves.scoringMoves().get(0);
    assertEquals(3, diagonalScoringMove1.score());

    var diagonalScoringMove2 = scoringMoves.scoringMoves().get(1);
    assertEquals(3, diagonalScoringMove2.score());

    var adjacentScoringMove = scoringMoves.scoringMoves().get(2);
    assertEquals(2, adjacentScoringMove.score());
  }

  @Test
  void testScoreDotAt_Level3_AllEightDirections() {
    // _____ 1 __ 2 __ 3 __ 4 __ 5
    // A | p1 | __ | p1 | __ | p1 |
    // B | __ | a1 | a1 | p1 | p1 |
    // C | __ | a1 | p1 | a1 | p1 |
    // D | __ | __ | a1 | a1 | __ |
    // E | __ | __ | __ | __ | __ |
    var moveHistory = moveHistory(player1, player2, List.of(
        "C3", "B3",
        "C5", "C4",
        "A5", "C2",
        "B5", "D3",
        "B4", "D4",
        "A1", "B2"));
    var move = new DotGame.Square("A3", player1);

    var scoringMoves = DotGame.ScoringMoves
        .create(player1)
        .scoreMove(move, DotGame.Board.Level.one, moveHistory);

    assertEquals(1, scoringMoves.totalScore());
    assertEquals(DotGame.ScoringMoveType.diagonal, scoringMoves.scoringMoves().get(0).type());

    var expectedMoves = List.of("A3", "B4", "C5");
    assertEquals(expectedMoves, scoringMoves.scoringMoves().get(0).scoringSquares());
  }

  @Test
  void testScoreMove_Level1_CenterDot_OneHorizontalLine() {
    // _____ 1 __ 2 __ 3 __ 4 __ 5
    // A | p2 | __ | p1 | p2 | p2 |
    // B | __ | __ | p1 | __ | __ |
    // C | p1 | p1 | p1 | p2 | __ |
    // D | __ | __ | p1 | __ | __ |
    // E | __ | __ | __ | __ | __ |
    var moveHistory = moveHistory(player1, player2, List.of(
        "A3", "A1",
        "B3", "A2",
        "C2", "A4",
        "C1", "A5",
        "D4", "C4"));
    var move = new DotGame.Square("C3", player1);

    var scoringMoves = DotGame.ScoringMoves
        .create(player1)
        .scoreMove(move, DotGame.Board.Level.one, moveHistory);

    assertEquals(3, scoringMoves.scoringMoves().size());
    assertEquals(DotGame.ScoringMoveType.horizontal, scoringMoves.scoringMoves().get(0).type());

    {
      var expectedMoves = List.of("C1", "C2", "C3");
      assertEquals(expectedMoves, scoringMoves.scoringMoves().get(0).scoringSquares());
      assertEquals(1, scoringMoves.scoringMoves().get(0).score());
      assertEquals(DotGame.ScoringMoveType.horizontal, scoringMoves.scoringMoves().get(0).type());
    }

    {
      var expectedMoves = List.of("A3", "B3", "C3");
      assertEquals(expectedMoves, scoringMoves.scoringMoves().get(1).scoringSquares());
      assertEquals(1, scoringMoves.scoringMoves().get(1).score());
      assertEquals(DotGame.ScoringMoveType.vertical, scoringMoves.scoringMoves().get(1).type());
    }

    {
      var expectedMoves = List.of("B3", "C2", "C3", "D4");
      assertEquals(expectedMoves, scoringMoves.scoringMoves().get(2).scoringSquares());
      assertEquals(2, scoringMoves.scoringMoves().get(2).score());
      assertEquals(DotGame.ScoringMoveType.adjacent, scoringMoves.scoringMoves().get(2).type());
    }
  }

  @Test
  void testScoreMoveAt_Level1_CenterDot_OneVerticalLine() {
    var moveHistory = moveHistory(player1, player2, List.of("A4", "A5", "B3", "C5", "C2", "E5", "B4", "D4", "C1"));
    var move = new DotGame.Square("C4", player1);

    var scoringMoves = DotGame.ScoringMoves
        .create(player1)
        .scoreMove(move, DotGame.Board.Level.one, moveHistory);

    assertEquals(1, scoringMoves.scoringMoves().size());
    assertEquals(DotGame.ScoringMoveType.vertical, scoringMoves.scoringMoves().get(0).type());

    var expectedMoves = List.of("A4", "B4", "C4");
    assertEquals(expectedMoves, scoringMoves.scoringMoves().get(0).scoringSquares());
  }

  @Test
  void testScoreDiagonalLineDownRight() {
    var moveHistory = moveHistory(player1, player2, List.of("A1", "A2", "B2", "A3", "A4", "A5"));
    var move = new DotGame.Square("C3", player1);

    var scoringMoves = DotGame.ScoringMoves
        .create(player1)
        .scoreMove(move, DotGame.Board.Level.one, moveHistory);

    assertEquals(1, scoringMoves.scoringMoves().size());
    assertEquals(DotGame.ScoringMoveType.diagonal, scoringMoves.scoringMoves().get(0).type());

    var expectedMoves = List.of("A1", "B2", "C3");
    assertEquals(expectedMoves, scoringMoves.scoringMoves().get(0).scoringSquares());
  }

  @Test
  void testScoreDiagonalLineDownLeft() {
    var moveHistory = moveHistory(player1, player2, List.of("A5", "A2", "B4", "A3", "A4", "A5"));
    var move = new DotGame.Square("C3", player1);

    var scoringMoves = DotGame.ScoringMoves
        .create(player1)
        .scoreMove(move, DotGame.Board.Level.one, moveHistory);

    assertEquals(1, scoringMoves.scoringMoves().size());
    assertEquals(DotGame.ScoringMoveType.diagonal, scoringMoves.scoringMoves().get(0).type());
    var expectedMoves = List.of("A5", "B4", "C3");
    assertEquals(expectedMoves, scoringMoves.scoringMoves().get(0).scoringSquares());
  }

  @Test
  void testIsDiagonal() {
    var move = new DotGame.Square("C3", player1);
    var direction = DotGame.ScoringMoves.DiagonalDirection.downRight;

    assertTrue(DotGame.ScoringMoves.isDiagonal(direction, move, new DotGame.Move("A1", player1.id())));
    assertTrue(DotGame.ScoringMoves.isDiagonal(direction, move, new DotGame.Move("B2", player1.id())));
    assertTrue(DotGame.ScoringMoves.isDiagonal(direction, move, new DotGame.Move("C3", player1.id())));
    assertTrue(DotGame.ScoringMoves.isDiagonal(direction, move, new DotGame.Move("D4", player1.id())));
    assertTrue(DotGame.ScoringMoves.isDiagonal(direction, move, new DotGame.Move("E5", player1.id())));

    assertFalse(DotGame.ScoringMoves.isDiagonal(direction, move, new DotGame.Move("A5", player1.id())));
    assertFalse(DotGame.ScoringMoves.isDiagonal(direction, move, new DotGame.Move("B4", player1.id())));
    assertTrue(DotGame.ScoringMoves.isDiagonal(direction, move, new DotGame.Move("C3", player1.id())));
    assertFalse(DotGame.ScoringMoves.isDiagonal(direction, move, new DotGame.Move("D2", player1.id())));
    assertFalse(DotGame.ScoringMoves.isDiagonal(direction, move, new DotGame.Move("E1", player1.id())));

    direction = DotGame.ScoringMoves.DiagonalDirection.downLeft;

    assertTrue(DotGame.ScoringMoves.isDiagonal(direction, move, new DotGame.Move("A5", player2.id())));
    assertTrue(DotGame.ScoringMoves.isDiagonal(direction, move, new DotGame.Move("B4", player2.id())));
    assertTrue(DotGame.ScoringMoves.isDiagonal(direction, move, new DotGame.Move("C3", player2.id())));
    assertTrue(DotGame.ScoringMoves.isDiagonal(direction, move, new DotGame.Move("D2", player2.id())));
    assertTrue(DotGame.ScoringMoves.isDiagonal(direction, move, new DotGame.Move("E1", player2.id())));

    assertFalse(DotGame.ScoringMoves.isDiagonal(direction, move, new DotGame.Move("A1", player2.id())));
    assertFalse(DotGame.ScoringMoves.isDiagonal(direction, move, new DotGame.Move("B2", player2.id())));
    assertTrue(DotGame.ScoringMoves.isDiagonal(direction, move, new DotGame.Move("C3", player2.id())));
    assertFalse(DotGame.ScoringMoves.isDiagonal(direction, move, new DotGame.Move("D4", player2.id())));
    assertFalse(DotGame.ScoringMoves.isDiagonal(direction, move, new DotGame.Move("E5", player2.id())));
  }

  @Test
  void testIsDiagonallyConsecutive() {
    var moves = moves(player1, List.of("A1", "B2", "C3", "D4", "E5"));
    var directionDownRight = DotGame.ScoringMoves.DiagonalDirection.downRight;
    var group = new ArrayList<DotGame.Move>();

    moves.forEach(m -> {
      if (group.isEmpty() || DotGame.ScoringMoves.isDiagonallyConsecutive(directionDownRight, group, m)) {
        group.add(m);
      }
    });
    assertEquals(group, moves);

    moves = moves(player1, List.of("A5", "B4", "C3", "D2", "E1"));
    var directionDownLeft = DotGame.ScoringMoves.DiagonalDirection.downLeft;
    group.clear();

    moves.forEach(m -> {
      if (group.isEmpty() || DotGame.ScoringMoves.isDiagonallyConsecutive(directionDownLeft, group, m)) {
        group.add(m);
      }
    });
    assertEquals(group, moves);
  }

  // move 1 - E1
  // move 2 - D2
  // move 3 - B4
  // move 4 - A5
  // move 5 - C3 - winning move, 3 points (A5, B4, C3), (E1, D2, C3), (D2, C3, B4)
  @Test
  void testScoreDiagonalFullLineDownLeftMultiplePoints() {
    var moveHistory = moves(player1, List.of("E1", "D2", "B4", "A5"));
    var move = new DotGame.Square("C3", player1);

    var scoringMoves = DotGame.ScoringMoves
        .create(player1)
        .scoreMove(move, DotGame.Board.Level.one, moveHistory);
    var scoringMove = scoringMoves.scoringMoves().get(0);

    assertEquals(1, scoringMoves.scoringMoves().size());
    assertEquals(3, scoringMove.score());
    assertEquals(DotGame.ScoringMoveType.diagonal, scoringMove.type());
    var expectedMoves = List.of("A5", "B4", "C3", "D2", "E1");
    assertEquals(expectedMoves, scoringMove.scoringSquares());
  }

  @Test
  void testScoreDiagonalFullLineDownRightMultiplePoints() {
    var moveHistory = moves(player1, List.of("A1", "B2", "D4", "E5"));
    var move = new DotGame.Square("C3", player1);

    var scoringMoves = DotGame.ScoringMoves
        .create(player1)
        .scoreMove(move, DotGame.Board.Level.one, moveHistory);
    var scoringMove = scoringMoves.scoringMoves().get(0);

    assertEquals(1, scoringMoves.scoringMoves().size());
    assertEquals(3, scoringMove.score());
    assertEquals(DotGame.ScoringMoveType.diagonal, scoringMove.type());
    var expectedMoves = List.of("A1", "B2", "C3", "D4", "E5");
    assertEquals(expectedMoves, scoringMove.scoringSquares());
  }

  @Test
  void testIsAdjacent() {
    var move = square("C3", player1);

    assertTrue(DotGame.ScoringMoves.isAdjacent(move, move("B2", player1)));
    assertTrue(DotGame.ScoringMoves.isAdjacent(move, move("B3", player1)));
    assertTrue(DotGame.ScoringMoves.isAdjacent(move, move("B4", player1)));
    assertTrue(DotGame.ScoringMoves.isAdjacent(move, move("C2", player1)));
    assertTrue(DotGame.ScoringMoves.isAdjacent(move, move("C4", player1)));
    assertTrue(DotGame.ScoringMoves.isAdjacent(move, move("D2", player1)));
    assertTrue(DotGame.ScoringMoves.isAdjacent(move, move("D3", player1)));
    assertTrue(DotGame.ScoringMoves.isAdjacent(move, move("D4", player1)));

    assertFalse(DotGame.ScoringMoves.isAdjacent(move, move("A1", player1)));
    assertFalse(DotGame.ScoringMoves.isAdjacent(move, move("A2", player1)));
    assertFalse(DotGame.ScoringMoves.isAdjacent(move, move("A3", player1)));
    assertFalse(DotGame.ScoringMoves.isAdjacent(move, move("A4", player1)));
    assertFalse(DotGame.ScoringMoves.isAdjacent(move, move("A5", player1)));

    assertFalse(DotGame.ScoringMoves.isAdjacent(move, move("B1", player1)));
    assertFalse(DotGame.ScoringMoves.isAdjacent(move, move("B5", player1)));
    assertFalse(DotGame.ScoringMoves.isAdjacent(move, move("C1", player1)));
    assertFalse(DotGame.ScoringMoves.isAdjacent(move, move("C5", player1)));
    assertFalse(DotGame.ScoringMoves.isAdjacent(move, move("D1", player1)));
    assertFalse(DotGame.ScoringMoves.isAdjacent(move, move("D5", player1)));

    assertFalse(DotGame.ScoringMoves.isAdjacent(move, move("E1", player1)));
    assertFalse(DotGame.ScoringMoves.isAdjacent(move, move("E2", player1)));
    assertFalse(DotGame.ScoringMoves.isAdjacent(move, move("E3", player1)));
    assertFalse(DotGame.ScoringMoves.isAdjacent(move, move("E4", player1)));
    assertFalse(DotGame.ScoringMoves.isAdjacent(move, move("E5", player1)));
  }

  @Test
  void testScoreAdjacentLevelOneWithMultipleLines() {
    var scoringMoves = DotGame.ScoringMoves
        .create(player1);

    // _____ 1 __ 2 __ 3 __ 4 __ 5
    // A | p2 | __ | p2 | __ | p2 |
    // B | __ | __ | p1 | __ | __ |
    // C | __ | p1 | __ | p1 | __ |
    // D | __ | __ | p1 | p1 | __ |
    // E | p2 | __ | p2 | __ | __ |
    var moveHistory = moveHistory(player1, player2, List.of(
        "B2", "A1",
        "B3", "A3",
        "D3", "A5",
        "D4", "E1",
        "C2", "E3"));
    var move = new DotGame.Square("C4", player1);

    scoringMoves = scoringMoves.scoreMove(move, DotGame.Board.Level.one, moveHistory);
    assertEquals(2, scoringMoves.totalScore());

    // _____ 1 __ 2 __ 3 __ 4 __ 5
    // A | p2 | __ | p2 | __ | p2 |
    // B | __ | p1 | p1 | __ | __ |
    // C | __ | p1 | p1 | p1 | __ |
    // D | __ | __ | p1 | p1 | __ |
    // E | p2 | __ | p2 | __ | p2 |
    moveHistory = moveHistory(player1, player2, List.of(
        "B2", "A1",
        "B3", "A3",
        "D3", "A5",
        "D4", "E1",
        "C2", "E3",
        "C4", "E5"));
    move = new DotGame.Square("C3", player1);

    scoringMoves = scoringMoves.scoreMove(move, DotGame.Board.Level.one, moveHistory);

    assertEquals(5, scoringMoves.scoringMoves().size());
    assertEquals(DotGame.ScoringMoveType.adjacent, scoringMoves.scoringMoves().get(0).type());
    assertEquals(2, scoringMoves.scoringMoves().get(0).score());
    assertEquals(DotGame.ScoringMoveType.horizontal, scoringMoves.scoringMoves().get(1).type());
    assertEquals(1, scoringMoves.scoringMoves().get(1).score());
    assertEquals(DotGame.ScoringMoveType.vertical, scoringMoves.scoringMoves().get(2).type());
    assertEquals(1, scoringMoves.scoringMoves().get(2).score());
    assertEquals(DotGame.ScoringMoveType.diagonal, scoringMoves.scoringMoves().get(3).type());
    assertEquals(1, scoringMoves.scoringMoves().get(3).score());
    assertEquals(DotGame.ScoringMoveType.adjacent, scoringMoves.scoringMoves().get(4).type());
    assertEquals(4, scoringMoves.scoringMoves().get(4).score());
    var expectedMoves = List.of("B2", "B3", "C2", "C3", "C4", "D3", "D4");
    assertEquals(expectedMoves, scoringMoves.scoringMoves().get(4).scoringSquares());

    assertEquals(9, scoringMoves.totalScore()); // 1 + 1 + 1 + 3, one for each line, and 3 for the adjacent squares
  }

  @Test
  void testAdjacentLevelNine() {
    var scoringMoves = DotGame.ScoringMoves
        .create(player1);

    var moveHistory = moveHistory(player1, player2, List.of(
        "A1", "A4",
        "A2", "A5",
        "A3", "A6",
        "B1", "B4",
        "B3", "B6",
        "C1", "C4",
        "C2", "C5",
        "C3", "C6"));
    var move = new DotGame.Square("B2", player1);

    scoringMoves = scoringMoves.scoreMove(move, DotGame.Board.Level.nine, moveHistory);
    assertEquals(2, scoringMoves.totalScore());

    assertEquals(2, scoringMoves.totalScore());
  }

  static DotGame.Square square(String id, DotGame.Player player) {
    return new DotGame.Square(id, player);
  }

  static DotGame.Move move(String id, DotGame.Player player) {
    return new DotGame.Move(id, player.id());
  }

  static List<DotGame.Move> moves(DotGame.Player player, List<String> ids) {
    return ids.stream()
        .map(d -> new DotGame.Move(d, player.id()))
        .toList();
  }

  static List<DotGame.Move> moveHistory(DotGame.Player player1, DotGame.Player player2, List<String> ids) {
    return IntStream.range(0, ids.size())
        .mapToObj(i -> new DotGame.Move(ids.get(i), i % 2 == 0 ? player1.id() : player2.id()))
        .toList();
  }

  static List<DotGame.Move> moves(DotGame.Board board) {
    return board.squares().stream()
        .filter(d -> d.playerId().isPresent())
        .map(d -> new DotGame.Move(d.squareId(), d.playerId().get()))
        .toList();
  }

  static List<DotGame.Square> squares(DotGame.Player player, List<String> ids) {
    return ids.stream()
        .map(d -> new DotGame.Square(d, player))
        .toList();
  }

  static List<DotGame.Square> squares(DotGame.Player player1, DotGame.Player player2, List<String> ids) {
    return IntStream.range(0, ids.size())
        .mapToObj(i -> new DotGame.Square(ids.get(i), i % 2 == 0 ? player1 : player2))
        .toList();
  }

  static List<DotGame.Square> squares(DotGame.Board board) {
    return board.squares().stream()
        .filter(d -> d.playerId().isPresent())
        .toList();
  }
}
