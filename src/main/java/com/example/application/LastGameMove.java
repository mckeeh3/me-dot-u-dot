package com.example.application;

import java.util.List;

import com.example.domain.DotGame;
import com.fasterxml.jackson.core.JsonProcessingException;

import akka.javasdk.JsonSupport;

interface LastGameMove {
  record Move(String squareId, String who) {
    static Move from(String agentId, DotGame.State gameState) {
      var lastMove = gameState.moveHistory().stream()
          .reduce((first, second) -> second)
          .orElse(null);
      var who = lastMove.playerId().equals(agentId) ? "you" : "opponent";
      return new Move(lastMove.squareId(), who);
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

  record ScoringMove(String moveSquareId, String type, int score, List<String> scoringSquareIds) {
    static ScoringMove from(DotGame.ScoringMove scoringMove) {
      var type = switch (scoringMove.type()) {
        case horizontal -> "horizontal line";
        case vertical -> "vertical line";
        case diagonal -> "diagonal line";
        case adjacent -> "multiple adjacent squares";
        case topToBottom -> "connected squares from top edge to bottom edge";
        case leftToRight -> "connected squares from left edge to right edge";
      };
      return new ScoringMove(scoringMove.move().squareId(), type, scoringMove.score(), scoringMove.scoringSquares());
    }
  }

  record ScoringMoves(List<ScoringMove> scoringMoves) {
    static ScoringMoves from(String squareId, DotGame.ScoringMoves scoringMoves) {
      return new ScoringMoves(scoringMoves.scoringMoves()
          .stream()
          .filter(sm -> sm.move().squareId().equals(squareId))
          .map(ScoringMove::from)
          .toList());
    }
  }

  record MoveScore(int delta, ScoringMoves scoringMoves) {
    static MoveScore from(String agentId, String squareId, DotGame.State gameState) {
      var scoringMoves = !agentId.equals(gameState.player1Status().player().id())
          ? gameState.player1Status().scoringMoves()
          : gameState.player2Status().scoringMoves();
      var delta = scoringMoves.scoringMoves()
          .stream()
          .filter(m -> m.move().squareId().equals(squareId))
          .map(m -> m.score())
          .reduce(0, Integer::sum);

      return new MoveScore(delta, ScoringMoves.from(squareId, scoringMoves));
    }
  }

  record Summary(Move move, CumulativeScore cumulativeScore, MoveScore moveScore) {
    static Summary from(String agentId, DotGame.State gameState) {
      var move = Move.from(agentId, gameState);
      var cumulativeScore = CumulativeScore.from(agentId, gameState);
      var moveScore = MoveScore.from(agentId, move.squareId(), gameState);

      return new Summary(move, cumulativeScore, moveScore);
    }
  }

  static String json(LastGameMove.Summary response) {
    var om = JsonSupport.getObjectMapper();
    try {
      return om.writerWithDefaultPrettyPrinter().writeValueAsString(response);
    } catch (JsonProcessingException e) {
      return "Opponent last move summary failed: %s".formatted(e.getMessage());
    }
  }
}
