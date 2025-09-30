package com.example.application;

import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.application.GameStateTool.BoardInfo;
import com.example.application.GameStateTool.GameInfo;
import com.example.domain.DotGame;

import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;

public class GameMoveTool {
  static final Logger log = LoggerFactory.getLogger(GameMoveTool.class);
  final ComponentClient componentClient;

  public GameMoveTool(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @FunctionTool(description = """
      Make a game move.

      - Use ONLY when it is your turn and the game is in progress.
      - Input: a single square coordinate string (e.g., "C3").
      - Do not include natural language or multiple coordinates.
      - This tool does NOT explain rules or validate strategy — it only records the move.

      The tool will return "Move completed" if the move was successful, otherwise it will return "Move rejected".
      """)
  public String makeMove(
      @Description("The ID of the game you are making a move in") String gameId,
      @Description("The ID of your player/agent id for this game") String agentId,
      @Description("""
          The square (board coordinate) to claim (e.g., "C3"). Squares IDs start
          at A1 in the top-left and extend to the board size determined by level
          """) String squareId) {
    log.debug("AgentId: {}, Make move: {} in game: {}", agentId, squareId, gameId);

    var command = new DotGame.Command.MakeMove(gameId, agentId, squareId);

    var stateBeforeMove = componentClient.forEventSourcedEntity(gameId)
        .method(DotGameEntity::getState)
        .invoke();

    var stateAfterMove = componentClient.forEventSourcedEntity(gameId)
        .method(DotGameEntity::makeMove)
        .invoke(command);

    var gameOver = stateAfterMove.status() != DotGame.Status.in_progress;
    var moveCompleted = stateBeforeMove.moveHistory().size() < stateAfterMove.moveHistory().size();
    var areYouCurrentPlayer = stateAfterMove.currentPlayer().isPresent() && stateAfterMove.currentPlayer().get().player().id().equals(agentId);

    if (moveCompleted && gameOver) {
      var result = "Move to %s completed, game over, you %s".formatted(squareId, stateAfterMove.status() == DotGame.Status.won_by_player ? "won" : "lost");
      log.debug(result);

      return result;
    }

    if (moveCompleted) {
      var result = "Move to %s completed, it's your opponent's turn".formatted(squareId);
      log.debug(result);

      return result;
    }

    var moveResult = "Move to %s %s, you %s the current player, %s"
        .formatted(
            squareId,
            (moveCompleted ? "completed" : "rejected"),
            (areYouCurrentPlayer ? "are" : "are not"),
            (areYouCurrentPlayer ? "it's still your turn, try again" : "it's your opponent's turn"));

    log.debug(moveResult);

    return moveResult;
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
  public MoveHistory getMoveHistory(
      @Description("The ID of the game you are playing and want to get the move history for") String gameId) {
    log.debug("Get game move history: {}", gameId);

    DotGame.State gameState = componentClient.forEventSourcedEntity(gameId)
        .method(DotGameEntity::getState)
        .invoke();

    return MoveHistory.from(gameState);
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
