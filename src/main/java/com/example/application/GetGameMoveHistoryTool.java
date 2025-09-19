package com.example.application;

import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.application.GetGameStateTool.Square;
import com.example.domain.DotGame;

import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;

public class GetGameMoveHistoryTool {
  static final Logger log = LoggerFactory.getLogger(GetGameMoveHistoryTool.class);
  final ComponentClient componentClient;

  public GetGameMoveHistoryTool(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @FunctionTool(description = """
      Retrieve the complete move history for a finished game so you can study what happened and decide how to evolve.

      - Use this after a game concludes to review every turn, including who played, think time, and the precise scoring move breakdowns.
      - Combine the insights you gather here with your playbook and system prompt updates—this data helps you choose which tactics to memorialise in the playbook and which behavioral adjustments belong in the system prompt.
      - The response contains move order, player IDs, per-move points, and the detailed scoring squares for each scoring move, giving you the evidence you need before calling `update_playbook` or `update_system_prompt`.

      IMPORTANT: It is important to review the move history after each game.
      Reviewing the move history enables you to improve your performance in future games.
      Consider updating your playbook and system prompt when you discover a stronger workflow or need to clarify how you should reason and respond.
      Updating your playbook and system prompt enables you to improve your performance in future games.
      Preserve the trustworthy foundations while evolving the areas that need refinement.
      """)
  public MoveHistory getGameMoveHistory(
      @Description("The ID of the game you are playing and want to get the move history for") String gameId) {
    log.debug("Get game move history: {}", gameId);

    DotGame.State gameState = componentClient.forEventSourcedEntity(gameId)
        .method(DotGameEntity::getState)
        .invoke();

    return MoveHistory.from(gameState);
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

  record BoardInfo(String level, Square topLeftSquare, Square bottomRightSquare) {
    static BoardInfo from(DotGame.Board board) {
      return new BoardInfo(
          board.level().name(),
          Square.from(board.squares().get(0)),
          Square.from(board.squares().get(board.squares().size() - 1)));
    }
  }

  record ScoringMove(String moveSquareId, String type, int score, List<String> scoringSquareIds) {
    static ScoringMove from(DotGame.ScoringMove scoringMove) {
      return new ScoringMove(scoringMove.move().squareId(), scoringMove.type().name(), scoringMove.score(), scoringMove.scoringSquares());
    }
  }

  record Move(String squareId, String playerId, long thinkMs, int moveScore, List<ScoringMove> scoringMoves) {
    static Move from(DotGame.Move move, DotGame.ScoringMoves player1ScoringMoves, DotGame.ScoringMoves player2ScoringMoves) {
      var p1ScoringMoves = player1ScoringMoves.scoringMoves()
          .stream()
          .filter(sm -> sm.move().squareId().equals(move.squareId()))
          .map(ScoringMove::from).toList();
      var p2ScoringMoves = player2ScoringMoves.scoringMoves()
          .stream()
          .filter(sm -> sm.move().squareId().equals(move.squareId()))
          .map(ScoringMove::from).toList();
      var scoringMoves = Stream.concat(p1ScoringMoves.stream(), p2ScoringMoves.stream()).toList();

      var newMoveScore = scoringMoves.stream().map(sm -> sm.score()).reduce(0, Integer::sum);

      return new Move(move.squareId(), move.playerId(), move.thinkMs(), newMoveScore, scoringMoves);
    }
  }

  public record MoveHistory(GameInfo gameInfo, BoardInfo boardInfo, List<Move> moves) {
    static MoveHistory from(DotGame.State gameState) {
      var p1ScoringMoves = gameState.player1Status().scoringMoves();
      var p2ScoringMoves = gameState.player2Status().scoringMoves();

      return new MoveHistory(GameInfo.from(gameState), BoardInfo.from(gameState.board()), gameState.moveHistory()
          .stream()
          .map(m -> Move.from(m, p1ScoringMoves, p2ScoringMoves))
          .toList());
    }
  }
}
