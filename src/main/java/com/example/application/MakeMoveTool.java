package com.example.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.DotGame;
import com.fasterxml.jackson.core.JsonProcessingException;

import akka.javasdk.JsonSupport;
import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;

public class MakeMoveTool {
  static final Logger log = LoggerFactory.getLogger(MakeMoveTool.class);
  final ComponentClient componentClient;
  final GameActionLogger gameLog;

  public MakeMoveTool(ComponentClient componentClient) {
    this.componentClient = componentClient;
    this.gameLog = new GameActionLogger(componentClient);
  }

  @FunctionTool(description = """
      Make a game move.

      - Use ONLY when it is your turn and the game is in progress.
      - Input: a single square coordinate string (e.g., "C3").
      - Do not include natural language or multiple coordinates.
      - This tool does NOT explain rules or validate strategy — it only records the move.

      The tool will return "Move completed" if the move was successful, otherwise it will return "Move rejected".
      """)
  public Response makeMove(
      @Description("The ID of the game you are making a move in") String gameId,
      @Description("The ID of your player/agent id for this game") String agentId,
      @Description("""
          The square (board coordinate) to claim (e.g., "C3"). Squares IDs start
          at A1 in the top-left and extend to the board size determined by level
          """) String squareId) {
    log.debug("GameId: {}, AgentId: {}, Make move: {}", gameId, agentId, squareId);

    var command = new DotGame.Command.MakeMove(gameId, agentId, squareId);

    var stateBeforeMove = componentClient.forEventSourcedEntity(gameId)
        .method(DotGameEntity::getState)
        .invoke();

    var stateAfterMove = componentClient.forEventSourcedEntity(gameId)
        .method(DotGameEntity::makeMove)
        .invoke(command);

    var gameOver = stateAfterMove.status() != DotGame.Status.in_progress;
    var moveCompleted = stateBeforeMove.moveHistory().size() < stateAfterMove.moveHistory().size();

    if (moveCompleted && gameOver) {
      var result = Response.from(agentId, squareId, stateBeforeMove, stateAfterMove);
      log.debug(json(result));
      gameLog.logToolCall(gameId, agentId, "makeMove", json(result));

      return Response.from(agentId, squareId, stateBeforeMove, stateAfterMove);
    }

    if (moveCompleted) {
      var result = Response.from(agentId, squareId, stateBeforeMove, stateAfterMove);
      log.debug(json(result));
      gameLog.logToolCall(gameId, agentId, "makeMove", json(result));

      return Response.from(agentId, squareId, stateBeforeMove, stateAfterMove);
    }

    var result = Response.from(agentId, squareId, stateBeforeMove, stateAfterMove);

    log.debug(json(result));
    gameLog.logToolCall(gameId, agentId, "makeMove", json(result));

    return Response.from(agentId, squareId, stateBeforeMove, stateAfterMove);
  }

  static String json(Response response) {
    var om = JsonSupport.getObjectMapper();
    try {
      return om.writerWithDefaultPrettyPrinter().writeValueAsString(response);
    } catch (JsonProcessingException e) {
      return "Make move tool response failed: %s".formatted(e.getMessage());
    }
  }

  public record MoveDetails(String squareId, String moveWas, String reason) {
    static MoveDetails from(String squareId, DotGame.State stateBeforeMove, DotGame.State stateAfterMove) {
      var moveCompleted = stateBeforeMove.moveHistory().size() < stateAfterMove.moveHistory().size();
      var moveWas = moveCompleted ? "completed" : "rejected";
      var squareNotAvailable = stateBeforeMove.moveHistory().stream().noneMatch(m -> m.squareId().equals(squareId));
      var reason = "Legal move, moved to an available square";

      if (!moveCompleted && squareNotAvailable) {
        reason = "Illegal move, it's not your turn";
      }
      if (!moveCompleted && !squareNotAvailable) {
        reason = "Illegal move, square %s is not available".formatted(squareId);
      }

      return new MoveDetails(squareId, moveWas, reason);
    }
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

  public record ScoringMoves(List<ScoringMove> scoringMoves) {
    static ScoringMoves from(String squareId, DotGame.ScoringMoves scoringMoves) {
      return new ScoringMoves(scoringMoves.scoringMoves()
          .stream()
          .filter(sm -> sm.move().squareId().equals(squareId))
          .map(ScoringMove::from)
          .toList());
    }
  }

  public record MoveScore(int delta, ScoringMoves scoringMoves) {
    static MoveScore from(String agentId, String squareId, DotGame.State gameState) {
      var scoringMoves = agentId.equals(gameState.player1Status().player().id())
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

  public record ActivePlayer(String who, String playerId, String reason) {
    static ActivePlayer from(String agentId, DotGame.State gameState) {
      var agentPlayerStatus = gameState.player1Status().player().id().equals(agentId)
          ? gameState.player1Status()
          : gameState.player2Status();

      if (gameState.status() != DotGame.Status.in_progress) {
        var reason = "The game is over, you %s".formatted(agentPlayerStatus.isWinner() ? "won" : "lost");
        return new ActivePlayer("", "", reason);
      }

      var nextPlayerId = gameState.currentPlayerStatus().get().player().id();
      var who = nextPlayerId.equals(agentId) ? "you" : "opponent";
      var reason = nextPlayerId.equals(agentId)
          ? "It's still your turn, try again"
          : "It's your opponent's turn";

      return new ActivePlayer(who, nextPlayerId, reason);
    }
  }

  public record Response(MoveDetails moveDetails, CumulativeScore cumulativeScore, MoveScore moveScore, ActivePlayer activePlayer) {
    static Response from(String agentId, String squareId, DotGame.State stateBeforeMove, DotGame.State stateAfterMove) {
      var moveDetails = MoveDetails.from(squareId, stateBeforeMove, stateAfterMove);
      var cumulativeScore = CumulativeScore.from(agentId, stateAfterMove);
      var moveScore = MoveScore.from(agentId, squareId, stateAfterMove);
      var activePlayer = ActivePlayer.from(agentId, stateAfterMove);

      return new Response(moveDetails, cumulativeScore, moveScore, activePlayer);
    }
  }
}
