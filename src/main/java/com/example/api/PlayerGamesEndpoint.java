package com.example.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.application.PlayerGamesEntity;
import com.example.application.PlayerGamesView;
import com.example.domain.PlayerGames;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/player-games")
public class PlayerGamesEndpoint {
  static final Logger log = LoggerFactory.getLogger(PlayerGamesEndpoint.class);
  final ComponentClient componentClient;

  public PlayerGamesEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/get-leader-board")
  public PlayerGamesView.LeaderBoard getLeaderBoard(PlayerGamesView.GetLeaderBoard request) {
    log.info("Get leader board");

    return componentClient.forView()
        .method(PlayerGamesView::getLeaderBoard)
        .invoke(request);
  }

  @Get("/get-player/{playerId}")
  public PlayerGames.State getPlayerGames(String playerId) {
    log.info("Get player games: {}", playerId);

    return componentClient.forEventSourcedEntity(playerId)
        .method(PlayerGamesEntity::getState)
        .invoke();
  }
}
