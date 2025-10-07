package com.example.application;

import static akka.Done.done;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;

import com.example.domain.Playbook;

@ComponentId("playbook-entity")
public class PlaybookEntity extends EventSourcedEntity<Playbook.State, Playbook.Event> {
  final Logger log = LoggerFactory.getLogger(getClass());
  final String entityId;

  public PlaybookEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public Playbook.State emptyState() {
    return Playbook.State.empty();
  }

  public Effect<Done> writePlaybook(Playbook.Command.WritePlaybook command) {
    log.debug("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command).stream().toList())
        .thenReply(newState -> done());
  }

  public ReadOnlyEffect<Playbook.State> getState() {
    log.debug("EntityId: {}\n_State: {}", entityId, currentState());

    return effects().reply(currentState());
  }

  @Override
  public Playbook.State applyEvent(Playbook.Event event) {
    log.debug("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);

    return switch (event) {
      case Playbook.Event.PlaybookUpdated e -> currentState().onEvent(e);
    };
  }
}
