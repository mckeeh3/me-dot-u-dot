package com.example.application;

import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.application.GameStateTool.BoardInfo;
import com.example.application.GameStateTool.GameInfo;
import com.example.domain.DotGame;
import com.fasterxml.jackson.core.JsonProcessingException;

import akka.javasdk.JsonSupport;
import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;

public class GameMoveTool {
  static final Logger log = LoggerFactory.getLogger(GameMoveTool.class);
  final ComponentClient componentClient;
  final GameActionLogger gameLog;

  public GameMoveTool(ComponentClient componentClient) {
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
  public MakeMoveTool.Response makeMove(
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
    // var areYouCurrentPlayer = stateAfterMove.currentPlayer().isPresent() &&
    // stateAfterMove.currentPlayer().get().player().id().equals(agentId);
    // var moveScorePlayer1 = stateAfterMove.player1Status().score() - stateBeforeMove.player1Status().score();
    // var moveScorePlayer2 = stateAfterMove.player2Status().score() - stateBeforeMove.player2Status().score();
    // var moveScore = moveScorePlayer1 + moveScorePlayer2;
    // var moveScoreMsg = moveScore < 1 ? "" : ", move scored %s point%s".formatted(moveScore, moveScore > 1 ? "s" : "");

    if (moveCompleted && gameOver) {
      // var playerStatus = stateAfterMove.status() == DotGame.Status.won_by_player ? "won" : "lost";
      // var result = "Move to %s completed%s, game over, you %s".formatted(squareId, moveScoreMsg, playerStatus);
      var result = MakeMoveTool.Response.from(agentId, squareId, stateBeforeMove, stateAfterMove);
      log.debug(json(result));
      gameLog.logToolCall(gameId, agentId, "makeMove", json(result));

      return MakeMoveTool.Response.from(agentId, squareId, stateBeforeMove, stateAfterMove);
    }

    if (moveCompleted) {
      // var result = "Move to %s completed%s, it's your opponent's turn".formatted(squareId, moveScoreMsg);
      var result = MakeMoveTool.Response.from(agentId, squareId, stateBeforeMove, stateAfterMove);
      log.debug(json(result));
      gameLog.logToolCall(gameId, agentId, "makeMove", json(result));

      return MakeMoveTool.Response.from(agentId, squareId, stateBeforeMove, stateAfterMove);
    }

    // var moveResult = "Move to %s rejected, you %s the current player, %s"
    // .formatted(
    // squareId,
    // (areYouCurrentPlayer ? "are" : "are not"),
    // (areYouCurrentPlayer ? "it's still your turn, try again" : "it's your opponent's turn"));

    var result = MakeMoveTool.Response.from(agentId, squareId, stateBeforeMove, stateAfterMove);

    log.debug(json(result));
    gameLog.logToolCall(gameId, agentId, "makeMove", json(result));

    return MakeMoveTool.Response.from(agentId, squareId, stateBeforeMove, stateAfterMove);
  }

  static String json(MakeMoveTool.Response response) {
    var om = JsonSupport.getObjectMapper();
    try {
      return om.writerWithDefaultPrettyPrinter().writeValueAsString(response);
    } catch (JsonProcessingException e) {
      return "Make move tool response failed: %s".formatted(e.getMessage());
    }
  }

  public interface MakeMoveTool {
    record MoveDetails(String squareId, String moveWas, String reason) {
      static MoveDetails from(String squareId, DotGame.State stateBeforeMove, DotGame.State gameState) {
        var moveCompleted = stateBeforeMove.moveHistory().size() < gameState.moveHistory().size();
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

    record CumulativeScore(int you, int opponent) {
      static CumulativeScore from(String agentId, DotGame.State stateBeforeMove, DotGame.State stateAfterMove) {
        var player1Id = stateBeforeMove.player1Status().player().id();
        var p1Score = stateAfterMove.player1Status().score();
        var p2Score = stateAfterMove.player2Status().score();
        var you = agentId.equals(player1Id) ? p1Score : p2Score;
        var opponent = agentId.equals(player1Id) ? p2Score : p1Score;

        return new CumulativeScore(you, opponent);
      }
    }

    record ScoringMove(String moveSquareId, String type, int score, List<String> scoringSquareIds) {
      static ScoringMove from(DotGame.ScoringMove scoringMove) {
        return new ScoringMove(scoringMove.move().squareId(), scoringMove.type().name(), scoringMove.score(), scoringMove.scoringSquares());
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
      static MoveScore from(String agentId, String squareId, DotGame.State stateBeforeMove, DotGame.State stateAfterMove) {
        var delta = stateAfterMove.player1Status().score() - stateBeforeMove.player1Status().score()
            + stateAfterMove.player2Status().score() - stateBeforeMove.player2Status().score();
        var scoringMoves = agentId.equals(stateAfterMove.player1Status().player().id())
            ? stateAfterMove.player1Status().scoringMoves()
            : stateAfterMove.player2Status().scoringMoves();

        return new MoveScore(delta, ScoringMoves.from(squareId, scoringMoves));
      }
    }

    record NextPlayer(String playerId, String reason) {
      static NextPlayer from(String agentId, DotGame.State stateBeforeMove, DotGame.State stateAfterMove) {
        var agentPlayerStatus = stateAfterMove.player1Status().player().id().equals(agentId)
            ? stateAfterMove.player1Status()
            : stateAfterMove.player2Status();

        if (stateAfterMove.status() != DotGame.Status.in_progress) {
          var reason = "The game is over, you %s".formatted(agentPlayerStatus.isWinner() ? "won" : "lost");
          return new NextPlayer("", reason);
        }

        var nextPlayerId = stateAfterMove.currentPlayer().get().player().id();
        var reason = nextPlayerId.equals(agentId)
            ? "It's still your turn, try again"
            : "It's your opponent's turn";

        return new NextPlayer(nextPlayerId, reason);
      }
    }

    public record Response(MoveDetails moveDetails, CumulativeScore cumulativeScore, MoveScore moveScore, NextPlayer nextPlayer) {
      static Response from(String agentId, String squareId, DotGame.State stateBeforeMove, DotGame.State stateAfterMove) {
        var moveDetails = MoveDetails.from(squareId, stateBeforeMove, stateAfterMove);
        var cumulativeScore = CumulativeScore.from(agentId, stateBeforeMove, stateAfterMove);
        var moveScore = MoveScore.from(agentId, squareId, stateBeforeMove, stateAfterMove);
        var nextPlayer = NextPlayer.from(agentId, stateBeforeMove, stateAfterMove);

        return new Response(moveDetails, cumulativeScore, moveScore, nextPlayer);
      }
    }
  }

  @FunctionTool(description = """
      Retrieve the complete move history for a finished game so you can study what happened and decide how to evolve.

      - Use this after a game concludes to review every turn, including who played, think time, and the precise scoring move breakdowns.
      - Combine the insights you gather here with your playbook and system prompt updates—this data helps you choose which tactics to memorialize in the playbook and which behavioral adjustments belong in the system prompt.
      - The response contains move order, player IDs, per-move points, and the detailed scoring squares for each scoring move, giving you the evidence you need before calling `update_playbook` or `update_system_prompt`.
      - Closely examine the move history for scoring moves as they provide valuable insights into your performance and opponent's performance in the game.
      - Scoring moves include the type of the pattern of moves, the score for the move, and a list of the squares that were scored.
      - Learning from scoring moves is essential to improve your performance in future games.

      IMPORTANT: It is important to review the move history after each game.
      Reviewing the move history enables you to improve your performance in future games.
      Consider updating your playbook and system prompt when you discover a stronger workflow or need to clarify how you should reason and respond.
      Updating your playbook and system prompt enables you to improve your performance in future games.
      Preserve the trustworthy foundations while evolving the areas that need refinement.
      """)
  public GetMoveHistoryTool.Response getMoveHistory(
      @Description("The ID of the game you are playing and want to get the move history for") String gameId,
      @Description("The ID of your player/agent id for this game") String agentId) {
    log.debug("GameId: {}, AgentId: {}, Get game move history", gameId, agentId);

    DotGame.State gameState = componentClient.forEventSourcedEntity(gameId)
        .method(DotGameEntity::getState)
        .invoke();

    var moveHistory = GetMoveHistoryTool.Response.from(gameState);

    if (!agentId.isEmpty()) {
      gameLog.logToolCall(gameId, agentId, "getMoveHistory", json(moveHistory));
    }

    return moveHistory;
  }

  static String json(GetMoveHistoryTool.Response moveHistory) {
    var om = JsonSupport.getObjectMapper();
    try {
      return om.writerWithDefaultPrettyPrinter().writeValueAsString(moveHistory);
    } catch (JsonProcessingException e) {
      return "Get move history failed: %s".formatted(e.getMessage());
    }
  }

  public interface GetMoveHistoryTool {
    record ScoringMove(String moveSquareId, String type, int score, List<String> scoringSquareIds) {
      static ScoringMove from(DotGame.ScoringMove scoringMove) {
        return new ScoringMove(scoringMove.move().squareId(), scoringMove.type().name(), scoringMove.score(), scoringMove.scoringSquares());
      }
    }

    record Move(String squareId, String playerId, int moveScore, long thinkMs, List<ScoringMove> scoringMoves) {
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

        return new Move(move.squareId(), move.playerId(), newMoveScore, move.thinkMs(), scoringMoves);
      }
    }

    public record Response(GameInfo gameInfo, BoardInfo boardInfo, List<Move> moves) {
      static Response from(DotGame.State gameState) {
        var p1ScoringMoves = gameState.player1Status().scoringMoves();
        var p2ScoringMoves = gameState.player2Status().scoringMoves();

        return new Response(GameInfo.from(gameState), BoardInfo.from(gameState.board()), gameState.moveHistory()
            .stream()
            .map(m -> Move.from(m, p1ScoringMoves, p2ScoringMoves))
            .toList());
      }
    }
  }
}
