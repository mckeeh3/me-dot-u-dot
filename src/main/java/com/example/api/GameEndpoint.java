package com.example.api;

import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.application.DotGameEntity;
import com.example.application.DotGameView;
import com.example.application.DotGameView.GetMoveStreamByGameIdRequest;
import com.example.application.MoveHistoryTool;
import com.example.application.MoveResponseLogsTool;
import com.example.application.MakeMoveTool;
import com.example.application.GameStateTool;
import com.example.domain.DotGame;
import com.example.domain.DotGame.Board;
import com.example.domain.DotGame.Player;
import com.typesafe.config.Config;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;

/**
 * Game endpoint for the me-dot-u-dot game. Handles player moves and game move responses.
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/game")
public class GameEndpoint {
  static final Logger log = LoggerFactory.getLogger(GameEndpoint.class);
  final Config config;
  final ComponentClient componentClient;

  public GameEndpoint(ComponentClient componentClient, Config config) {
    this.componentClient = componentClient;
    this.config = config;
  }

  @Post("/create-game")
  public GameResponse createGame(CreateGame request) {
    log.debug("Create game: {}", request);

    var command = new DotGame.Command.CreateGame(request.gameId, request.player1, request.player2, request.level);

    var gameState = componentClient
        .forEventSourcedEntity(request.gameId)
        .method(DotGameEntity::createGame)
        .invoke(command);

    return new GameResponse(gameState);
  }

  @Post("/make-move")
  public GameResponse makeMove(MakeMove request) {
    log.debug("Make move: {}", request);

    var command = new DotGame.Command.MakeMove(request.gameId, request.playerId, request.squareId);

    var gameState = componentClient
        .forEventSourcedEntity(request.gameId)
        .method(DotGameEntity::makeMove)
        .invoke(command);

    return new GameResponse(gameState);
  }

  @Post("/cancel-game")
  public GameResponse cancelGame(CancelGame request) {
    log.debug("Cancel game: {}", request);

    var reason = "User cancelled game";
    var command = new DotGame.Command.CancelGame(request.gameId, reason);

    var gameState = componentClient
        .forEventSourcedEntity(request.gameId)
        .method(DotGameEntity::cancelGame)
        .invoke(command);

    return new GameResponse(gameState);
  }

  @Get("/get-move-stream-by-game-id/{gameId}")
  public HttpResponse getMoveStreamByGameId(String gameId) {
    log.debug("Get move stream by game id: {}", gameId);

    var request = new GetMoveStreamByGameIdRequest(gameId);
    var gameState = componentClient
        .forView()
        .stream(DotGameView::getMoveStreamByGameId)
        .source(request);

    return HttpResponses.serverSentEvents(gameState);
  }

  @Get("/get-state/{gameId}")
  public GameResponse getState(String gameId) {
    log.debug("Get state for game: {}", gameId);

    var gameState = componentClient
        .forEventSourcedEntity(gameId)
        .method(DotGameEntity::getState)
        .invoke();

    return new GameResponse(gameState);
  }

  @Get("/get-current-in-progress-game")
  public String getCurrentInProgressGame() {
    log.debug("Get current in progress game");

    try {
      var dotGameRow = componentClient
          .forView()
          .method(DotGameView::getCurrentInProgressGame)
          .invoke();

      return "{\"gameId\": \"%s\"}".formatted(dotGameRow.gameId());
    } catch (akka.javasdk.client.NoEntryFoundException e) {
      return "{ \"gameId\": null }";
    } catch (Exception e) {
      log.error("Error getting current in progress game", e);
      return "{ \"gameId\": null }";
    }
  }

  @Post("/get-recent-games")
  public DotGameView.GamesPage getRecentGames(DotGameView.GetRecentGamesRequest request) {
    log.debug("Get recent games: {}", request);
    return componentClient.forView()
        .method(DotGameView::getRecentGames)
        .invoke(request);
  }

  @Post("/get-games-by-player-id-paged")
  public DotGameView.GamesPage getGamesByPlayerIdPaged(DotGameView.GetGamesByPlayerIdPagedRequest request) {
    log.debug("Get games by player id paged: {}", request);

    return componentClient
        .forView()
        .method(DotGameView::getGamesByPlayerIdPaged)
        .invoke(request);
  }

  @Get("/get-all-ai-agent-models")
  public List<String> getAllAiAgentModels() {
    return config.root()
        .entrySet().stream()
        .filter(entry -> entry.getKey().startsWith("ai-agent-model-"))
        .map(entry -> entry.getKey())
        .map(key -> key.replace("ai-agent-model-", ""))
        .sorted()
        .toList();
  }

  @Get("/get-game-state-tool/{gameId}")
  public GameStateTool.GameState getGameStateTool(String gameId) {
    log.debug("Get game state tool: {}", gameId);
    return new GameStateTool(componentClient).getGameState(gameId, "");
  }

  @Get("/get-game-move-history/{gameId}")
  public GetMoveHistory.Response getGameMoveHistory(String gameId) {
    log.debug("Get game move history, gameId: {}, {}", gameId);
    return getMoveHistoryResponse(gameId);
  }

  @Get("/get-game-move-history-tool/{gameId}")
  public MoveHistoryTool.Response getGameMoveHistoryTool(String gameId) {
    log.debug("Get game move history tool: {}", gameId);
    return new MoveHistoryTool(componentClient).getMoveHistory(gameId, "");
  }

  @Get("/get-move-response-logs-tool/{gameId}/{agentId}")
  public MoveResponseLogsTool.Response getGameMoveLogsTool(String gameId, String agentId) {
    log.debug("Get game move logs tool: {}, {}", gameId, agentId);
    return new MoveResponseLogsTool(componentClient).getMoveResponseLogs(gameId, agentId);
  }

  @Get("/make-move-tool-test/{gameId}/{agentId}/{squareId}")
  public MakeMoveTool.Response getMakeMoveTool(String gameId, String agentId, String squareId) {
    log.debug("Get make move tool: {}, {}, {}", gameId, agentId, squareId);
    return new MakeMoveTool(componentClient).makeMove(gameId, agentId, squareId);
  }

  GetMoveHistory.Response getMoveHistoryResponse(String gameId) {
    log.debug("Get move history response: {}", gameId);

    var gameState = componentClient
        .forEventSourcedEntity(gameId)
        .method(DotGameEntity::getState)
        .invoke();

    return GetMoveHistory.Response.from(gameState, "");
  }

  public record CreateGame(String gameId, Player player1, Player player2, Board.Level level) {}

  public record MakeMove(String gameId, String playerId, String squareId) {}

  public record CancelGame(String gameId) {}

  public record GameResponse(DotGame.State gameState) {}

  public interface GetMoveHistory {

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

    record Move(String squareId, String who, String playerId, int moveScore, long thinkMs, List<ScoringMove> scoringMoves) {
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

        return new Move(move.squareId(), who, move.playerId(), newMoveScore, move.thinkMs(), scoringMoves);
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
