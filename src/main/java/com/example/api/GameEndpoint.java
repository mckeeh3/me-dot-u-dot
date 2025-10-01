package com.example.api;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.application.DotGameEntity;
import com.example.application.DotGameView;
import com.example.application.DotGameView.GetMoveStreamByGameIdRequest;
import com.example.application.GameMoveTool;
import com.example.application.GameMoveTool.MoveHistory;
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

    var command = new DotGame.Command.CancelGame(request.gameId);

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

  @Get("/get-recent-games")
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
  public GameStateTool.CompactGameState getGameStateTool(String gameId) {
    log.debug("Get game state tool: {}", gameId);
    return new GameStateTool(componentClient).getGameState(gameId);
  }

  @Get("/get-game-move-history-tool/{gameId}")
  public MoveHistory getGameMoveHistoryTool(String gameId) {
    log.debug("Get game move history tool: {}", gameId);
    // return new GameMoveHistoryTool(componentClient).getMoveHistory(gameId);
    return new GameMoveTool(componentClient).getMoveHistory(gameId);
  }

  public record CreateGame(String gameId, Player player1, Player player2, Board.Level level) {}

  public record MakeMove(String gameId, String playerId, String squareId) {}

  public record CancelGame(String gameId) {}

  public record GameResponse(DotGame.State gameState) {}
}
