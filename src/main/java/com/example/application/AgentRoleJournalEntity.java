package com.example.application;

import static akka.Done.done;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import com.example.domain.AgentRoleJournal;

@ComponentId("agent-role-journal-entity")
public class AgentRoleJournalEntity extends EventSourcedEntity<AgentRoleJournal.State, AgentRoleJournal.Event> {
  final Logger log = LoggerFactory.getLogger(getClass());
  final String entityId;

  public AgentRoleJournalEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public AgentRoleJournal.State emptyState() {
    return AgentRoleJournal.State.empty();
  }

  public Effect<Done> createAgentRoleJournal(AgentRoleJournal.Command.CreateAgentRoleJournal command) {
    log.debug("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command).stream().toList())
        .thenReply(newState -> done());
  }

  public ReadOnlyEffect<AgentRoleJournal.State> getState() {
    log.debug("EntityId: {}\n_State: {}", entityId, currentState());

    return effects().reply(currentState());
  }

  @Override
  public AgentRoleJournal.State applyEvent(AgentRoleJournal.Event event) {
    log.debug("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);

    return switch (event) {
      case AgentRoleJournal.Event.AgentRoleJournalCreated e -> currentState().onEvent(e);
    };
  }
}
