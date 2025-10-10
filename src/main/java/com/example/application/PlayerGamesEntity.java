package com.example.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.PlayerGames;

import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;

@Component(id = "player-games-entity")
public class PlayerGamesEntity extends EventSourcedEntity<PlayerGames.State, PlayerGames.Event> {
  final Logger log = LoggerFactory.getLogger(getClass());
  final String entityId;

  public PlayerGamesEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public PlayerGames.State emptyState() {
    return PlayerGames.State.empty();
  }

  public Effect<PlayerGames.State> addGame(PlayerGames.Command.AddGame command) {
    log.debug("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command).stream().toList())
        .thenReply(newState -> newState);
  }

  public Effect<PlayerGames.State> addGameToBranch(PlayerGames.Command.AddGameToBranch command) {
    log.debug("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command).stream().toList())
        .thenReply(newState -> newState);
  }

  public Effect<PlayerGames.State> updateSubBranchStats(PlayerGames.Command.UpdateSubBranchStats command) {
    log.debug("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command).stream().toList())
        .thenReply(newState -> newState);
  }

  public ReadOnlyEffect<PlayerGames.State> getState() {
    log.debug("EntityId: {}\n_State: {}", entityId, currentState());

    if (currentState().isEmpty()) {
      return effects().error("PlayerGames '%s' not found".formatted(entityId));
    }

    return effects().reply(currentState());
  }

  @Override
  public PlayerGames.State applyEvent(PlayerGames.Event event) {
    log.debug("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);

    return switch (event) {
      case PlayerGames.Event.GameAdded e -> currentState().onEvent(e);
      case PlayerGames.Event.DelegatedGameToSubBranch e -> currentState().onEvent(e);
      case PlayerGames.Event.StatsUpdated e -> currentState().onEvent(e);
      case PlayerGames.Event.ParentUpdateRequired e -> currentState().onEvent(e);
    };
  }
}
