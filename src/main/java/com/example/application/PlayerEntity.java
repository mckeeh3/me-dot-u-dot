package com.example.application;

import static akka.Done.done;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.Player;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;

@ComponentId("player-entity")
public class PlayerEntity extends KeyValueEntity<Player.State> {
  static final Logger log = LoggerFactory.getLogger(PlayerEntity.class);
  String entityId;

  public PlayerEntity(KeyValueEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public Player.State emptyState() {
    return Player.State.empty();
  }

  public Effect<Done> createPlayer(Player.Command.CreatePlayer command) {
    log.debug("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .updateState(currentState().onCommand(command))
        .thenReply(done());
  }

  public Effect<Done> updatePlayer(Player.Command.UpdatePlayer command) {
    log.debug("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .updateState(currentState().onCommand(command))
        .thenReply(done());
  }

  public Effect<Player.State> getState() {
    log.debug("EntityId: {}\n_State: {}", entityId, currentState());

    if (currentState().isEmpty()) {
      return effects().error("Player '%s' not found".formatted(entityId));
    }

    return effects().reply(currentState());
  }
}
