package com.example.application;

import static akka.Done.done;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;

import com.example.domain.AgentRole;

@ComponentId("agent-role-entity")
public class AgentRoleEntity extends EventSourcedEntity<AgentRole.State, AgentRole.Event> {
  final Logger log = LoggerFactory.getLogger(getClass());
  final String entityId;

  public AgentRoleEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public AgentRole.State emptyState() {
    return AgentRole.State.empty();
  }

  public Effect<Done> updateAgentRole(AgentRole.Command.UpdateAgentRole command) {
    log.debug("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command).stream().toList())
        .thenReply(newState -> done());
  }

  public Effect<AgentRole.State> getState() {
    log.debug("EntityId: {}\n_State: {}", entityId, currentState());

    if (currentState().isEmpty()) {
      var command = new AgentRole.Command.CreateAgentRole(entityId);
      log.debug("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

      return effects()
          .persistAll(currentState().onCommand(command).stream().toList())
          .thenReply(newState -> newState);
    }

    return effects().reply(currentState());
  }

  @Override
  public AgentRole.State applyEvent(AgentRole.Event event) {
    log.debug("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);

    return switch (event) {
      case AgentRole.Event.AgentRoleCreated e -> currentState().onEvent(e);
      case AgentRole.Event.AgentRoleUpdated e -> currentState().onEvent(e);
    };
  }
}
