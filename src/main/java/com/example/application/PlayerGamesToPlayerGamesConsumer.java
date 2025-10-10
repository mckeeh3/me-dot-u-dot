package com.example.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.PlayerGames;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;

@Component(id = "player-games-to-player-games-consumer")
@Consume.FromEventSourcedEntity(PlayerGamesEntity.class)
public class PlayerGamesToPlayerGamesConsumer extends Consumer {
  final Logger log = LoggerFactory.getLogger(getClass());
  final ComponentClient componentClient;

  public PlayerGamesToPlayerGamesConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onEvent(PlayerGames.Event event) {
    return switch (event) {
      case PlayerGames.Event.GameAdded e -> onEvent(e);
      case PlayerGames.Event.DelegatedGameToSubBranch e -> onEvent(e);
      case PlayerGames.Event.StatsUpdated e -> onEvent(e);
      case PlayerGames.Event.ParentUpdateRequired e -> onEvent(e);
      default -> effects().done();
    };
  }

  Effect onEvent(PlayerGames.Event.GameAdded event) {
    return effects().done();
  }

  Effect onEvent(PlayerGames.Event.DelegatedGameToSubBranch event) {
    log.debug("Event: {}", event);

    var command = new PlayerGames.Command.AddGameToBranch(
        event.subBranchId(),
        event.playerId(),
        event.gameId(),
        event.parentBranchId(),
        event.stats());

    componentClient.forEventSourcedEntity(event.subBranchId())
        .method(PlayerGamesEntity::addGameToBranch)
        .invoke(command);

    return effects().done();
  }

  Effect onEvent(PlayerGames.Event.ParentUpdateRequired event) {
    log.debug("Event: {}", event);

    var command = new PlayerGames.Command.UpdateSubBranchStats(
        event.parentBranchId(),
        event.updatedSubBranchId(),
        event.updatedSubBranchStats());

    componentClient.forEventSourcedEntity(event.parentBranchId())
        .method(PlayerGamesEntity::updateSubBranchStats)
        .invoke(command);

    return effects().done();
  }

  Effect onEvent(PlayerGames.Event.StatsUpdated event) {
    return effects().done();
  }
}
