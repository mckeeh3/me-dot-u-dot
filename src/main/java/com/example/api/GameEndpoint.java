package com.example.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.application.DotGameEntity;
import com.example.domain.DotGame;
import com.example.domain.DotGame.Board;
import com.example.domain.DotGame.Player;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;

/**
 * Game endpoint for the me-dot-u-dot game. Handles player moves and AI responses.
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint
public class GameEndpoint {
  static final Logger log = LoggerFactory.getLogger(GameEndpoint.class);

  public record CreateGame(String gameId, Player player1, Player player2, Board.Level level) {}

  public record MakeMove(String gameId, String playerId, String dotId) {}

  public record GameResponse(DotGame.State gameState) {}

  final ComponentClient componentClient;

  public GameEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/api/game/create-game")
  public GameResponse createGame(CreateGame request) {
    log.info("Create game: {}", request);

    var command = new DotGame.Command.CreateGame(request.gameId, request.player1, request.player2, request.level);

    var gameState = componentClient
        .forEventSourcedEntity(request.gameId)
        .method(DotGameEntity::createGame)
        .invoke(command);

    return new GameResponse(gameState);
  }

  @Post("/api/game/make-move")
  public GameResponse makeMove(MakeMove request) {
    log.info("Make move: {}", request);

    var command = new DotGame.Command.MakeMove(request.gameId, request.playerId, request.dotId);

    var gameState = componentClient
        .forEventSourcedEntity(request.gameId)
        .method(DotGameEntity::makeMove)
        .invoke(command);

    return new GameResponse(gameState);
  }
}
