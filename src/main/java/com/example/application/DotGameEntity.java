package com.example.application;

import static akka.Done.done;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import com.example.domain.DotGame;

@ComponentId("dot-game-entity")
public class DotGameEntity extends EventSourcedEntity<DotGame.State, DotGame.Event> {
  final Logger log = LoggerFactory.getLogger(getClass());
  final String entityId;

  public DotGameEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public DotGame.State emptyState() {
    return DotGame.State.empty();
  }

  public Effect<Done> createGame(DotGame.Command.CreateGame command) {
    log.debug("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command).stream().toList())
        .thenReply(newState -> done());
  }

  public Effect<DotGame.State> makeMove(DotGame.Command.MakeMove command) {
    log.debug("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command))
        .thenReply(newState -> newState);
  }

  public Effect<DotGame.State> getState() {
    log.debug("EntityId: {}\n_State: {}", entityId, currentState());

    if (currentState().isEmpty()) {
      return effects().error("Game '%s' not found".formatted(entityId));
    }

    return effects().reply(currentState());
  }

  @Override
  public DotGame.State applyEvent(DotGame.Event event) {
    log.debug("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);

    return switch (event) {
      case DotGame.Event.GameCreated e -> currentState().onEvent(e);
      case DotGame.Event.MoveMade e -> currentState().onEvent(e);
      case DotGame.Event.GameCompleted e -> currentState().onEvent(e);
    };
  }
}
