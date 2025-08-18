package com.example.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.application.DotGameEntity;
import com.example.application.DotGameView;
import com.example.application.PlaybookJournalView;
import com.example.application.DotGameView.GetMoveStreamByGameIdRequest;
import com.example.domain.DotGame;
import com.example.domain.DotGame.Board;
import com.example.domain.DotGame.Player;

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

  public record CreateGame(String gameId, Player player1, Player player2, Board.Level level) {}

  public record MakeMove(String gameId, String playerId, String dotId) {}

  public record CancelGame(String gameId) {}

  public record GameResponse(DotGame.State gameState) {}

  public record JournalRequest(String agentId, long sequenceId) {}

  final ComponentClient componentClient;

  public GameEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/create-game")
  public GameResponse createGame(CreateGame request) {
    log.info("Create game: {}", request);

    var command = new DotGame.Command.CreateGame(request.gameId, request.player1, request.player2, request.level);

    var gameState = componentClient
        .forEventSourcedEntity(request.gameId)
        .method(DotGameEntity::createGame)
        .invoke(command);

    return new GameResponse(gameState);
  }

  @Post("/make-move")
  public GameResponse makeMove(MakeMove request) {
    log.info("Make move: {}", request);

    var command = new DotGame.Command.MakeMove(request.gameId, request.playerId, request.dotId);

    var gameState = componentClient
        .forEventSourcedEntity(request.gameId)
        .method(DotGameEntity::makeMove)
        .invoke(command);

    return new GameResponse(gameState);
  }

  @Post("/cancel-game")
  public GameResponse cancelGame(CancelGame request) {
    log.info("Cancel game: {}", request);

    var command = new DotGame.Command.CancelGame(request.gameId);

    var gameState = componentClient
        .forEventSourcedEntity(request.gameId)
        .method(DotGameEntity::cancelGame)
        .invoke(command);

    return new GameResponse(gameState);
  }

  @Get("/get-move-stream-by-game-id/{gameId}")
  public HttpResponse getMoveStreamByGameId(String gameId) {
    log.info("Get move stream by game id: {}", gameId);

    var request = new GetMoveStreamByGameIdRequest(gameId);
    var gameState = componentClient
        .forView()
        .stream(DotGameView::getMoveStreamByGameId)
        .source(request);

    return HttpResponses.serverSentEvents(gameState);
  }

  @Get("/get-state/{gameId}")
  public GameResponse getState(String gameId) {
    log.info("Get state for game: {}", gameId);

    var gameState = componentClient
        .forEventSourcedEntity(gameId)
        .method(DotGameEntity::getState)
        .invoke();

    return new GameResponse(gameState);
  }

  @Get("/get-journal-by-agent-id-down/{agentId}")
  public PlaybookJournalView.JournalRow getJournalByAgentIdDown(JournalRequest request) {
    log.info("Get journal by agent id: {}", request);

    var queryRequest = new PlaybookJournalView.GetByAgentIdDownRequest(request.agentId(), request.sequenceId());

    return componentClient
        .forView()
        .method(PlaybookJournalView::getByAgentIdDown)
        .invoke(queryRequest);
  }

  @Get("/get-journal-by-agent-id-up/{agentId}")
  public PlaybookJournalView.JournalRow getJournalByAgentIdUp(JournalRequest request) {
    log.info("Get journal by agent id: {}", request);

    var queryRequest = new PlaybookJournalView.GetByAgentIdUpRequest(request.agentId(), request.sequenceId());

    return componentClient
        .forView()
        .method(PlaybookJournalView::getByAgentIdUp)
        .invoke(queryRequest);
  }
}
