package com.example.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.DotGame;

import akka.javasdk.client.ComponentClient;
import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;

public class GetGameStateTool {
  static final Logger log = LoggerFactory.getLogger(GetGameStateTool.class);
  final ComponentClient componentClient;

  public GetGameStateTool(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @FunctionTool(description = """
      Retrieve the current game state in a compact format.

      - Returns CompactGameState.State with fields:
        - gameId: unique game identifier
        - status: empty | in_progress | won_by_player | draw | canceled
        - level: one..nine (board size: one=5x5 .. nine=21x21)
        - board: occupied cells only, e.g. "A1:p1,B2:p2"
        - p1 / p2: "id|name|type|moves|score|winner"
        - turn: current player ID (null if not in progress)
        - moves: chronological moves, e.g. "A1:p1,B2:p2"
      - Coordinates: A1 = top-left, columns A–U, rows 1–21 depending on level.
      - This is the authoritative source for board state, scores, and whose turn it is.
      """)
  public CompactGameState getGameState(
      @Description("The ID of the game you are playing") String gameId) {
    log.debug("Get game state: {}", gameId);

    DotGame.State fullState = componentClient.forEventSourcedEntity(gameId)
        .method(DotGameEntity::getState)
        .invoke();

    return CompactGameState.from(fullState);
  }

  /**
   * Compact game state with minimal token overhead.
   *
   * @param gameId  Game identifier
   * @param created Game creation timestamp (ISO-8601)
   * @param status  Game status: empty|in_progress|won_by_player|draw|canceled
   * @param level   Board level (one=5x5, two=7x7, ..., nine=21x21)
   * @param board   Compact board representation as "A1:p1,B2:p2,C3:p1" where only occupied dots are listed
   * @param p1      Player 1 info: "id|name|type|moves|score|winner"
   * @param p2      Player 2 info: "id|name|type|moves|score|winner"
   * @param turn    Current player ID (null if game not in progress)
   * @param moves   Move history as "A1:p1,B2:p2,C3:p1"
   */
  public record CompactGameState(
      String gameId,
      String created,
      String status,
      String level,
      String board,
      String p1,
      String p2,
      String turn,
      String moves) {

    /**
     * Convert from full DotGame.State to compact representation
     */
    static CompactGameState from(DotGame.State gameState) {
      // Compact board: only occupied dots as "A1:p1,B2:p2"
      var boardBuilder = new StringBuilder();
      gameState.board().dots().stream()
          .filter(dot -> dot.player().isPresent())
          .forEach(dot -> {
            if (boardBuilder.length() > 0)
              boardBuilder.append(",");
            boardBuilder.append(dot.id()).append(":").append(dot.player().get().id());
          });

      // Compact player info: "id|name|type|moves|score|winner"
      var p1 = formatPlayer(gameState.player1Status());
      var p2 = formatPlayer(gameState.player2Status());

      // Current turn player ID
      var turn = gameState.currentPlayer()
          .map(player -> player.player().id())
          .orElse(null);

      // Compact moves: "A1:p1,B2:p2"
      var movesBuilder = new StringBuilder();
      gameState.moveHistory().forEach(move -> {
        if (movesBuilder.length() > 0)
          movesBuilder.append(",");
        movesBuilder.append(move.dotId()).append(":").append(move.playerId());
      });

      return new CompactGameState(
          gameState.gameId(),
          gameState.createdAt().toString(),
          gameState.status().name(),
          gameState.board().level().name(),
          boardBuilder.toString(),
          p1,
          p2,
          turn,
          movesBuilder.toString());
    }

    static String formatPlayer(DotGame.PlayerStatus playerStatus) {
      DotGame.Player player = playerStatus.player();
      return String.format("%s|%s|%s|%d|%d|%s",
          player.id(),
          player.name(),
          player.type().name(),
          playerStatus.moves(),
          playerStatus.score(),
          playerStatus.isWinner());
    }
  }
}
