package com.example.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

public class DotGameTest {

  DotGame.Board level1Board;
  DotGame.Board level3Board;
  DotGame.Player player1;
  DotGame.Player player2;

  @BeforeEach
  void setUp() {
    level1Board = DotGame.Board.of(DotGame.Board.Level.one); // 5x5
    level3Board = DotGame.Board.of(DotGame.Board.Level.three); // 9x9
    player1 = new DotGame.Player("player1", DotGame.PlayerType.human, "Alice");
    player2 = new DotGame.Player("player2", DotGame.PlayerType.human, "Bob");
  }

  @Test
  void testScoreDotAt_Level1_CenterDot_NoLines() {
    // Place dot at C3 with no other dots around it
    var board = level1Board.withDot("C3", player1);

    // Required length for level 1: (5/2) + 1 = 3
    // C3 alone can't form any lines of length 3
    assertEquals(0, board.scoreDotAt("C3"));
  }

  @Test
  void testScoreDotAt_Level1_CenterDot_OneHorizontalLine() {
    // Create horizontal line: A3, B3, C3
    var board = level1Board
        .withDot("A3", player1)
        .withDot("B3", player1)
        .withDot("C3", player1);

    // Should score 1 point for the horizontal line of length 3
    assertEquals(1, board.scoreDotAt("C3"));
  }

  @Test
  void testScoreDotAt_Level1_CenterDot_OneVerticalLine() {
    // Create vertical line: C1, C2, C3
    var board = level1Board
        .withDot("C1", player1)
        .withDot("C2", player1)
        .withDot("C3", player1);

    // Should score 1 point for the vertical line of length 3
    assertEquals(1, board.scoreDotAt("C3"));
  }

  @Test
  void testScoreDotAt_Level1_CenterDot_OneDiagonalLine() {
    // Create diagonal line: A1, B2, C3
    var board = level1Board
        .withDot("A1", player1)
        .withDot("B2", player1)
        .withDot("C3", player1);

    // Should score 1 point for the diagonal line of length 3
    assertEquals(1, board.scoreDotAt("C3"));
  }

  @Test
  void testScoreDotAt_Level1_CenterDot_MultipleLines() {
    // Create multiple lines from C3:
    // Horizontal: A3, B3, C3
    // Vertical: C1, C2, C3
    // Diagonal: A1, B2, C3
    var board = level1Board
        .withDot("A3", player1)
        .withDot("B3", player1)
        .withDot("C1", player1)
        .withDot("C2", player1)
        .withDot("A1", player1)
        .withDot("B2", player1)
        .withDot("C3", player1);

    // Should score 3 points (horizontal, vertical, diagonal)
    assertEquals(3, board.scoreDotAt("C3"));
  }

  @Test
  void testScoreDotAt_Level1_EdgeDot_LimitedLines() {
    // Place dot at E5 (bottom right)
    // Only possible lines:
    // Horizontal: C5, D5, E5
    // Vertical: E3, E4, E5
    // Diagonal: C3, D4, E5
    var board = level1Board
        .withDot("C5", player1)
        .withDot("D5", player1)
        .withDot("E3", player1)
        .withDot("E4", player1)
        .withDot("C3", player1)
        .withDot("D4", player1)
        .withDot("E5", player1);

    // Should score 3 points (only 3 directions are possible from E5)
    assertEquals(3, board.scoreDotAt("E5"));
  }

  @Test
  void testScoreDotAt_Level1_EdgeDot_TooShortLines() {
    // Place dot at E5 with only 2 dots in each direction
    var board = level1Board
        .withDot("D5", player1)
        .withDot("E4", player1)
        .withDot("E5", player1);

    // Lines are too short (length 2 < required 3)
    assertEquals(0, board.scoreDotAt("E5"));
  }

  @Test
  void testScoreDotAt_Level1_CornerDot_VeryLimitedLines() {
    // Place dot at A1 (top left)
    // Only possible lines:
    // Horizontal: A1, A2, A3
    // Vertical: A1, B1, C1
    // Diagonal: A1, B2, C3
    var board = level1Board
        .withDot("A2", player1)
        .withDot("A3", player1)
        .withDot("B1", player1)
        .withDot("C1", player1)
        .withDot("B2", player1)
        .withDot("C3", player1)
        .withDot("A1", player1);

    // Should score 3 points (only 3 directions are possible from A1)
    assertEquals(3, board.scoreDotAt("A1"));
  }

  @Test
  void testScoreDotAt_Level1_LineWithGaps_NoScore() {
    // Create line with gaps: A3, C3 (missing B3)
    var board = level1Board
        .withDot("A3", player1)
        .withDot("C3", player1);

    // No consecutive line of length 3
    assertEquals(0, board.scoreDotAt("C3"));
  }

  @Test
  void testScoreDotAt_Level1_LineWithDifferentPlayer_NoScore() {
    // Create line with different player in middle: A3, B3(player2), C3
    var board = level1Board
        .withDot("A3", player1)
        .withDot("B3", player2)
        .withDot("C3", player1);

    // No consecutive line of same player
    assertEquals(0, board.scoreDotAt("C3"));
  }

  @Test
  void testScoreDotAt_Level3_LongerLines() {
    // Required length for level 3: (9/2) + 1 = 5
    // Create horizontal line: A5, B5, C5, D5, E5
    var board = level3Board
        .withDot("A5", player1)
        .withDot("B5", player1)
        .withDot("C5", player1)
        .withDot("D5", player1)
        .withDot("E5", player1);

    // Should score 1 point for the horizontal line of length 5
    assertEquals(1, board.scoreDotAt("E5"));
  }

  @Test
  void testScoreDotAt_Level3_LineTooShort() {
    // Create line of length 4, but required is 5
    var board = level3Board
        .withDot("A5", player1)
        .withDot("B5", player1)
        .withDot("C5", player1)
        .withDot("D5", player1);

    // Line too short
    assertEquals(0, board.scoreDotAt("D5"));
  }

  @Test
  void testScoreDotAt_EmptyDot_NoScore() {
    // Test empty dot
    assertEquals(0, level1Board.scoreDotAt("C3"));
  }

  @Test
  void testScoreDotAt_InvalidCoordinates_NoScore() {
    // Test invalid coordinates
    assertEquals(0, level1Board.scoreDotAt("Z99"));
  }

  @Test
  void testScoreDotAt_Level1_AllEightDirections() {
    // Create lines in all 8 directions from C3
    // Horizontal: A3, B3, C3 (right)
    // Horizontal: C3, D3, E3 (left)
    // Vertical: C1, C2, C3 (down)
    // Vertical: C3, C4, C5 (up)
    // Diagonal: A1, B2, C3 (down-right)
    // Diagonal: C3, D4, E5 (up-left)
    // Diagonal: E1, D2, C3 (down-left)
    // Diagonal: C3, B4, A5 (up-right)
    var board = level1Board
        .withDot("A3", player1)
        .withDot("B3", player1)
        .withDot("C1", player1)
        .withDot("C2", player1)
        .withDot("C4", player1)
        .withDot("C5", player1)
        .withDot("D3", player1)
        .withDot("E3", player1)
        .withDot("A1", player1)
        .withDot("B2", player1)
        .withDot("D4", player1)
        .withDot("E5", player1)
        .withDot("E1", player1)
        .withDot("D2", player1)
        .withDot("B4", player1)
        .withDot("A5", player1)
        .withDot("C3", player1);

    // C3 is in the middle of all 8 lines, so it should score 4 points for 4 new lines
    assertEquals(4, board.scoreDotAt("C3"));

    // Test that the end dots score correctly based on the actual board setup
    assertEquals(3, board.scoreDotAt("A3")); // End of horizontal, diagonal down-right, diagonal down-left
    assertEquals(3, board.scoreDotAt("E3")); // End of horizontal, diagonal up-right, diagonal up-left
    assertEquals(3, board.scoreDotAt("C1")); // End of vertical, diagonal down-right, diagonal up-right
    assertEquals(3, board.scoreDotAt("C5")); // End of vertical, diagonal up-left, diagonal down-left
    assertEquals(1, board.scoreDotAt("A1")); // End of diagonal down-right only (A1-B2-C3)
    assertEquals(1, board.scoreDotAt("E5")); // End of diagonal up-left only (E5-D4-C3)
    assertEquals(1, board.scoreDotAt("E1")); // End of diagonal down-left only (E1-D2-C3)
    assertEquals(1, board.scoreDotAt("A5")); // End of diagonal up-right only (A5-B4-C3)
  }
}
