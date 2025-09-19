package com.example.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.application.PlayerEntity;
import com.example.application.PlayerView;
import com.example.application.PlayerView.Players;
import com.example.domain.Player;

import akka.Done;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/player")
public class PlayerEndpoint {
  static final Logger log = LoggerFactory.getLogger(PlayerEndpoint.class);
  final ComponentClient componentClient;

  public PlayerEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/create-player")
  public Done createPlayer(Player.Command.CreatePlayer command) {
    log.debug("Create player: {}", command);

    return componentClient.forKeyValueEntity(command.id())
        .method(PlayerEntity::createPlayer)
        .invoke(command);
  }

  @Post("/update-player")
  public Done updatePlayer(Player.Command.UpdatePlayer command) {
    log.debug("Update player: {}", command);

    return componentClient.forKeyValueEntity(command.id())
        .method(PlayerEntity::updatePlayer)
        .invoke(command);
  }

  @Get("/get-player/{id}")
  public Player.State getPlayer(String id) {
    log.debug("Get player: {}", id);

    return componentClient.forKeyValueEntity(id)
        .method(PlayerEntity::getState)
        .invoke();
  }

  @Get("/get-players")
  public Players getPlayers() {
    log.debug("Get players");

    return componentClient.forView()
        .method(PlayerView::getAllNames)
        .invoke();
  }
}
