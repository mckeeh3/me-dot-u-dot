package com.example.application;

import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.DotGame;
import com.fasterxml.jackson.core.JsonProcessingException;

import akka.javasdk.JsonSupport;
import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;

public class GameStateTool {
  static final Logger log = LoggerFactory.getLogger(GameStateTool.class);
  final ComponentClient componentClient;
  final GameActionLogger gameLog;

  public GameStateTool(ComponentClient componentClient) {
    this.componentClient = componentClient;
    this.gameLog = new GameActionLogger(componentClient);
  }

  @FunctionTool(description = """
      Retrieve the current game state as a structured GameState object.

      Returns GameState with nested objects:
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
  public GameState getGameState(
      @Description("The ID of the game you are playing and want to get the move history for") String gameId,
      @Description("The ID of your agent id for this game") String agentId) {
    log.debug("GameId: {}, AgentId: {}, Get game state", gameId, agentId);

    DotGame.State fullState = componentClient.forEventSourcedEntity(gameId)
        .method(DotGameEntity::getState)
        .invoke();

    var gameState = GameState.from(agentId, fullState);

    gameLog.logToolCall(gameId, agentId, "getGameState", json(gameState));

    return gameState;
  }

  String json(GameState gameState) {
    var om = JsonSupport.getObjectMapper();
    try {
      return om.writerWithDefaultPrettyPrinter().writeValueAsString(gameState);
    } catch (JsonProcessingException e) {
      return "Get game state failed: %s".formatted(e.getMessage());
    }
  }

  public record GameState(
      GameInfo gameInfo,
      CumulativeScore cumulativeScore,
      ActivePlayer activePlayer,
      BoardInfo boardInfo,
      AvailableSquares availableSquares,
      MoveHistory moveHistory) {

    static GameState from(String agentId, DotGame.State gameState) {
      return new GameState(
          GameInfo.from(gameState),
          CumulativeScore.from(agentId, gameState),
          ActivePlayer.from(agentId, gameState),
          BoardInfo.from(gameState.board()),
          AvailableSquares.from(gameState.board()),
          MoveHistory.from(agentId, gameState));
    }
  }

  record GameInfo(String gameId, String status) {
    static GameInfo from(DotGame.State gameState) {
      var status = switch (gameState.status()) {
        case empty -> "empty";
        case in_progress -> "in progress";
        case won_by_player -> "won by player";
        case draw -> "draw";
        case canceled -> "canceled";
      };

      return new GameInfo(gameState.gameId(), status);
    }
  }

  record CumulativeScore(int you, int opponent) {
    static CumulativeScore from(String agentId, DotGame.State gameState) {
      var player1Id = gameState.player1Status().player().id();
      var p1Score = gameState.player1Status().score();
      var p2Score = gameState.player2Status().score();
      var you = agentId.equals(player1Id) ? p1Score : p2Score;
      var opponent = agentId.equals(player1Id) ? p2Score : p1Score;

      return new CumulativeScore(you, opponent);
    }
  }

  record ActivePlayer(String who, String playerId) {
    static ActivePlayer from(String agentId, DotGame.State gameState) {
      var who = gameState.currentPlayerStatus().map(p -> p.player().id().equals(agentId) ? "you" : "opponent").orElse("none");
      var playerId = gameState.currentPlayerStatus().map(p -> p.player().id()).orElse("");
      return new ActivePlayer(who, playerId);
    }
  }

  record Square(String squareId, int row, int column) {
    static Square from(DotGame.Square square) {
      return new Square(square.squareId(), square.row(), square.col());
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

  record AvailableSquares(List<String> availableSquareIds) {
    static AvailableSquares from(DotGame.Board board) {
      return new AvailableSquares(board.squares()
          .stream()
          .filter(square -> square.playerId().isEmpty())
          .map(square -> square.squareId())
          .toList());
    }
  }

  record Move(String squareId, String who, String playerId, int moveScore, List<ScoringMove> scoringMoves) {
    static Move from(DotGame.Move move, String agentId, DotGame.ScoringMoves player1ScoringMoves, DotGame.ScoringMoves player2ScoringMoves) {
      var who = agentId.equals(move.playerId()) ? "you" : "opponent";
      var p1ScoringMoves = player1ScoringMoves.scoringMoves()
          .stream()
          .filter(scoringMove -> scoringMove.move().squareId().equals(move.squareId()))
          .map(ScoringMove::from).toList();
      var p2ScoringMoves = player2ScoringMoves.scoringMoves()
          .stream()
          .filter(scoringMove -> scoringMove.move().squareId().equals(move.squareId()))
          .map(ScoringMove::from).toList();
      var scoringMoves = Stream.concat(p1ScoringMoves.stream(), p2ScoringMoves.stream()).toList();

      var newMoveScore = scoringMoves.stream().map(sm -> sm.score()).reduce(0, Integer::sum);

      return new Move(move.squareId(), who, move.playerId(), newMoveScore, scoringMoves);
    }
  }

  public record MoveHistory(List<Move> moves) {
    static MoveHistory from(String agentId, DotGame.State gameState) {
      var p1ScoringMoves = gameState.player1Status().scoringMoves();
      var p2ScoringMoves = gameState.player2Status().scoringMoves();

      return new MoveHistory(gameState.moveHistory()
          .stream()
          .map(move -> Move.from(move, agentId, p1ScoringMoves, p2ScoringMoves))
          .toList());
    }
  }

  record ScoringMove(String moveSquareId, String type, int score, List<String> scoringSquareIds) {
    static ScoringMove from(DotGame.ScoringMove scoringMove) {
      var type = switch (scoringMove.type()) {
        case horizontal -> "horizontal line";
        case vertical -> "vertical line";
        case diagonal -> "diagonal line";
        case adjacent -> "adjacent cluster";
        case topToBottom -> "connected squares from top edge to bottom edge";
        case leftToRight -> "connected squares from left edge to right edge";
      };
      return new ScoringMove(scoringMove.move().squareId(), type, scoringMove.score(), scoringMove.scoringSquares());
    }
  }
}
