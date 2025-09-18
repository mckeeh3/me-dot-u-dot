package com.example.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.DotGame;

import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;

public class GetGameStateTool {
  static final Logger log = LoggerFactory.getLogger(GetGameStateTool.class);
  final ComponentClient componentClient;

  public GetGameStateTool(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @FunctionTool(description = """
      Retrieve the current game state as a structured CompactGameState object.

      Returns CompactGameState with nested objects:
      - gameInfo: {gameId, createdAt, status, currentPlayerId}
        - status: empty | in_progress | won_by_player | draw | canceled
        - currentPlayerId: ID of player whose turn it is (null if game not in progress)
      - players: {players: [player1, player2]}
        - Each player: {id, name, type, moves, score, winner}
        - type: human | agent
        - winner: boolean indicating if this player won
      - boardInfo: {level, topLeftSquare, bottomRightSquare}
        - level: one..nine (board size: one=5x5 .. nine=21x21)
        - squares: {squareId, row, column} for board bounds
      - moveHistory: {moves: [{squareId, playerId}, ...]}
        - Chronological list of all moves made in the game

      Coordinates: A1 = top-left, columns A–U, rows 1–21 depending on level.
      This is the authoritative source for board state, scores, and whose turn it is.
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
      GameInfo gameInfo,
      Players players,
      BoardInfo boardInfo,
      EmptySquares emptySquares,
      MoveHistory moveHistory,
      ScoringMoves player1ScoringMoves,
      ScoringMoves player2ScoringMoves) {

    /**
     * Convert from full DotGame.State to compact representation
     */
    static CompactGameState from(DotGame.State gameState) {
      return new CompactGameState(
          GameInfo.from(gameState),
          Players.from(gameState),
          BoardInfo.from(gameState.board()),
          EmptySquares.from(gameState.board()),
          MoveHistory.from(gameState.moveHistory()),
          ScoringMoves.from(gameState.player1Status().scoringMoves()),
          ScoringMoves.from(gameState.player2Status().scoringMoves()));
    }
  }

  record GameInfo(String gameId, String createdAt, String status, String currentPlayerId) {
    static GameInfo from(DotGame.State gameState) {
      return new GameInfo(
          gameState.gameId(),
          gameState.createdAt().toString(),
          gameState.status().name(),
          gameState.currentPlayer().map(p -> p.player().id()).orElse(null));
    }
  }

  record Square(String squareId, int row, int column) {
    static Square from(DotGame.Square dot) {
      return new Square(dot.squareId(), dot.row(), dot.col());
    }
  }

  record BoardInfo(String level, Square topLeftSquare, Square bottomRightSquare) {
    static BoardInfo from(DotGame.Board board) {
      return new BoardInfo(
          board.level().name(),
          Square.from(board.squares().get(0)),
          Square.from(board.squares().get(board.squares().size() - 1)));
    }
  }

  record Player(String id, String name, String type, int moves, int score, boolean winner) {
    static Player from(DotGame.PlayerStatus playerStatus) {
      return new Player(
          playerStatus.player().id(),
          playerStatus.player().name(),
          playerStatus.player().type().name(),
          playerStatus.moves(),
          playerStatus.score(),
          playerStatus.isWinner());
    }
  }

  record Players(List<Player> players) {
    static Players from(DotGame.State gameState) {
      return new Players(List.of(Player.from(gameState.player1Status()), Player.from(gameState.player2Status())));
    }
  }

  record EmptySquares(List<String> emptySquareIds) {
    static EmptySquares from(DotGame.Board board) {
      return new EmptySquares(board.squares()
          .stream()
          .filter(d -> d.playerId().isEmpty())
          .map(DotGame.Square::squareId)
          .toList());
    }
  }

  record Move(String squareId, String playerId) {
    static Move from(DotGame.Move move) {
      return new Move(move.squareId(), move.playerId());
    }
  }

  record MoveHistory(List<Move> moves) {
    static MoveHistory from(List<DotGame.Move> moves) {
      return new MoveHistory(moves.stream().map(Move::from).toList());
    }
  }

  record ScoringMove(String moveSquareId, String type, int score, List<String> scoringSquareIds) {
    static ScoringMove from(DotGame.ScoringMove scoringMove) {
      return new ScoringMove(scoringMove.move().squareId(), scoringMove.type().name(), scoringMove.score(), scoringMove.scoringSquares());
    }
  }

  record ScoringMoves(String playerId, int totalScore, List<ScoringMove> scoringMoves) {
    static ScoringMoves from(DotGame.ScoringMoves scoringMoves) {
      return new ScoringMoves(scoringMoves.playerId(), scoringMoves.totalScore(), scoringMoves.scoringMoves().stream().map(ScoringMove::from).toList());
    }
  }
}
