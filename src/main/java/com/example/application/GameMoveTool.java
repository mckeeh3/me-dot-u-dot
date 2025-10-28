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

    if (moveCompleted && gameOver) {
      var result = MakeMoveTool.Response.from(agentId, squareId, stateBeforeMove, stateAfterMove);
      log.debug(json(result));
      gameLog.logToolCall(gameId, agentId, "makeMove", json(result));

      return MakeMoveTool.Response.from(agentId, squareId, stateBeforeMove, stateAfterMove);
    }

    if (moveCompleted) {
      var result = MakeMoveTool.Response.from(agentId, squareId, stateBeforeMove, stateAfterMove);
      log.debug(json(result));
      gameLog.logToolCall(gameId, agentId, "makeMove", json(result));

      return MakeMoveTool.Response.from(agentId, squareId, stateBeforeMove, stateAfterMove);
    }

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

    record ActivePlayer(String who, String playerId, String reason) {
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

    var moveHistory = GetMoveHistoryTool.Response.from(gameState, agentId);

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

    record GameInfo(String gameId, String status) {
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
}
