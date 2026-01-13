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

public class MoveHistoryTool {
  static final Logger log = LoggerFactory.getLogger(MoveHistoryTool.class);
  final ComponentClient componentClient;
  final GameActionLogger gameLog;

  public MoveHistoryTool(ComponentClient componentClient) {
    this.componentClient = componentClient;
    this.gameLog = new GameActionLogger(componentClient);
  }

  @FunctionTool(description = """
      Retrieve the complete move history for a finished game so you can study what happened and decide how to evolve.

      - The response contains move order, player IDs, per-move points, and the detailed scoring squares for each scoring move.
      - Scoring moves include the type of the scoring pattern, the score for the move, and a list of the squares that were scored.
      """)
  public Response getMoveHistory(
      @Description("The ID of the game you are playing and want to get the move history for") String gameId,
      @Description("The ID of your player/agent id for this game") String agentId) {
    log.debug("GameId: {}, AgentId: {}, Get game move history", gameId, agentId);

    DotGame.State gameState = componentClient.forEventSourcedEntity(gameId)
        .method(DotGameEntity::getState)
        .invoke();

    var moveHistory = Response.from(gameState, agentId);

    if (!agentId.isEmpty()) {
      gameLog.logToolCall(gameId, agentId, "getMoveHistory", json(moveHistory));
    }

    return moveHistory;
  }

  static String json(Response moveHistory) {
    var om = JsonSupport.getObjectMapper();
    try {
      return om.writerWithDefaultPrettyPrinter().writeValueAsString(moveHistory);
    } catch (JsonProcessingException e) {
      return "Get move history failed: %s".formatted(e.getMessage());
    }
  }

  public record GameInfo(String gameId, String status) {
    static GameInfo from(String agentId, DotGame.State gameState) {
      var status = switch (gameState.status()) {
        case empty -> "empty";
        case in_progress -> "in progress";
        case won_by_player -> wonByPlayer(agentId, gameState);
        case draw -> "draw";
        case canceled -> "canceled";
      };

      return new GameInfo(gameState.gameId(), status);
    }
  }

  static String wonByPlayer(String agentId, DotGame.State gameState) {
    var winningPlayerId = gameState.player1Status().isWinner()
        ? gameState.player1Status().player().id()
        : gameState.player2Status().player().id();
    return agentId.equals(winningPlayerId) ? "you won" : "you lost";
  }

  public record CumulativeScore(int you, int opponent) {
    static CumulativeScore from(String agentId, DotGame.State gameState) {
      var player1Id = gameState.player1Status().player().id();
      var p1Score = gameState.player1Status().score();
      var p2Score = gameState.player2Status().score();
      var you = agentId.equals(player1Id) ? p1Score : p2Score;
      var opponent = agentId.equals(player1Id) ? p2Score : p1Score;

      return new CumulativeScore(you, opponent);
    }
  }

  public record Square(String squareId, int row, int column) {
    static Square from(DotGame.Square square) {
      return new Square(square.squareId(), square.row(), square.col());
    }
  }

  public record BoardInfo(String level, Square topLeftSquare, Square bottomRightSquare) {
    static BoardInfo from(DotGame.Board board) {
      return new BoardInfo(
          board.level().name(),
          Square.from(board.squares().get(0)),
          Square.from(board.squares().get(board.squares().size() - 1)));
    }
  }

  public record ScoringMove(String moveSquareId, String type, int score, List<String> scoringSquareIds) {
    static ScoringMove from(DotGame.ScoringMove scoringMove) {
      var type = switch (scoringMove.type()) {
        case horizontal -> "horizontal line";
        case vertical -> "vertical line";
        case diagonal -> "diagonal line";
        case adjacent -> "adjacent squares";
        case topToBottom -> "connected squares from top edge to bottom edge";
        case leftToRight -> "connected squares from left edge to right edge";
      };
      return new ScoringMove(scoringMove.move().squareId(), type, scoringMove.score(), scoringMove.scoringSquares());
    }
  }

  public record Move(String squareId, String who, String playerId, int moveScore, List<ScoringMove> scoringMoves) {
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

  public record Response(GameInfo gameInfo, CumulativeScore cumulativeScore, BoardInfo boardInfo, List<Move> moves) {
    static Response from(DotGame.State gameState, String agentId) {
      var p1ScoringMoves = gameState.player1Status().scoringMoves();
      var p2ScoringMoves = gameState.player2Status().scoringMoves();
      var gameInfo = GameInfo.from(agentId, gameState);
      var cumulativeScore = CumulativeScore.from(agentId, gameState);
      var boardInfo = BoardInfo.from(gameState.board());

      return new Response(gameInfo, cumulativeScore, boardInfo, gameState.moveHistory()
          .stream()
          .map(move -> Move.from(move, agentId, p1ScoringMoves, p2ScoringMoves))
          .toList());
    }
  }
}
