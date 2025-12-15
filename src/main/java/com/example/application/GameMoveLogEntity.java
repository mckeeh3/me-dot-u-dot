package com.example.application;

import static akka.Done.done;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.GameMoveLog;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;

@Component(id = "game-move-log-entity")
public class GameMoveLogEntity extends EventSourcedEntity<GameMoveLog.State, GameMoveLog.Event> {
  final Logger log = LoggerFactory.getLogger(getClass());
  final String entityId;

  public GameMoveLogEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public GameMoveLog.State emptyState() {
    return GameMoveLog.State.empty();
  }

  public Effect<Done> createGameMoveLog(GameMoveLog.Command.CreateGameMoveLog command) {
    log.debug("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command).stream().toList())
        .thenReply(newState -> done());
  }

  public ReadOnlyEffect<GameMoveLog.State> getState() {
    log.debug("EntityId: {}\n_State: {}", entityId, currentState());

    if (currentState().isEmpty()) {
      return effects().error("Make move response '%s' not found".formatted(entityId));
    }

    return effects().reply(currentState());
  }

  @Override
  public GameMoveLog.State applyEvent(GameMoveLog.Event event) {
    log.debug("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);

    return switch (event) {
      case GameMoveLog.Event.GameMoveLogCreated e -> currentState().onEvent(e);
    };
  }
}
