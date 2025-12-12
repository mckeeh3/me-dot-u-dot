package com.example.application;

import static akka.Done.done;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.GameLog;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;

@Component(id = "make-move-response-entity")
public class GameLogEntity extends EventSourcedEntity<GameLog.State, GameLog.Event> {
  final Logger log = LoggerFactory.getLogger(getClass());
  final String entityId;

  public GameLogEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public GameLog.State emptyState() {
    return GameLog.State.empty();
  }

  public Effect<Done> createMakeMoveResponse(GameLog.Command.CreateMakeMoveResponse command) {
    log.debug("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command).stream().toList())
        .thenReply(newState -> done());
  }

  public ReadOnlyEffect<GameLog.State> getState() {
    log.debug("EntityId: {}\n_State: {}", entityId, currentState());

    if (currentState().isEmpty()) {
      return effects().error("Make move response '%s' not found".formatted(entityId));
    }

    return effects().reply(currentState());
  }

  @Override
  public GameLog.State applyEvent(GameLog.Event event) {
    log.debug("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);

    return switch (event) {
      case GameLog.Event.MakeMoveResponseCreated e -> currentState().onEvent(e);
    };
  }
}
