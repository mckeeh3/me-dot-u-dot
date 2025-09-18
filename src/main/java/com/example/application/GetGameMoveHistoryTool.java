package com.example.application;

import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
      Retrieve the move history for a given game.

      Returns a list of moves with the scoring moves for each move.
      """)
  public MoveHistory getGameMoveHistory(
      @Description("The ID of a game you want to get the move history for") String gameId) {
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

  record Move(String squareId, String playerId, long thinkMs, List<ScoringMove> scoringMoves) {
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

      return new Move(move.squareId(), move.playerId(), move.thinkMs(), scoringMoves);
    }
  }

  public record MoveHistory(List<Move> moves) {
    static MoveHistory from(DotGame.State gameState) {
      var p1ScoringMoves = gameState.player1Status().scoringMoves();
      var p2ScoringMoves = gameState.player2Status().scoringMoves();

      return new MoveHistory(gameState.moveHistory()
          .stream()
          .map(m -> Move.from(m, p1ScoringMoves, p2ScoringMoves))
          .toList());
    }
  }
}
