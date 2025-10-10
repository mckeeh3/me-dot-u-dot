package com.example.application;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.AgentRole;
import com.example.domain.AgentRoleJournal;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;

@Component(id = "agent-role-to-agent-role-journal-consumer")
@Consume.FromEventSourcedEntity(AgentRoleEntity.class)
public class AgentRoleToAgentRoleJournalConsumer extends Consumer {
  final Logger log = LoggerFactory.getLogger(getClass());
  final ComponentClient componentClient;

  public AgentRoleToAgentRoleJournalConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onEvent(AgentRole.Event event) {
    log.debug("Event: {}", event);

    return switch (event) {
      case AgentRole.Event.AgentRoleCreated e -> onEvent(e);
      case AgentRole.Event.AgentRoleUpdated e -> onEvent(e);
      case AgentRole.Event.AgentRoleReset e -> onEvent(e);
      default -> {
        log.debug("Unknown event: {}", event);
        yield effects().done();
      }
    };
  }

  Effect onEvent(AgentRole.Event.AgentRoleCreated event) {
    var agentId = event.agentId();
    var sequence = messageContext().metadata().asCloudEvent().sequence().orElse(Instant.now().toEpochMilli());
    var journalId = agentId + ":" + sequence;

    var command = new AgentRoleJournal.Command.CreateAgentRoleJournal(
        agentId,
        sequence,
        event.systemPrompt(),
        event.createdAt());

    componentClient
        .forEventSourcedEntity(journalId)
        .method(AgentRoleJournalEntity::createAgentRoleJournal)
        .invoke(command);

    return effects().done();
  }

  Effect onEvent(AgentRole.Event.AgentRoleUpdated event) {
    var agentId = event.agentId();
    var sequence = messageContext().metadata().asCloudEvent().sequence().orElse(Instant.now().toEpochMilli());
    var journalId = agentId + ":" + sequence;

    var command = new AgentRoleJournal.Command.CreateAgentRoleJournal(
        agentId,
        sequence,
        event.systemPrompt(),
        event.updatedAt());

    componentClient
        .forEventSourcedEntity(journalId)
        .method(AgentRoleJournalEntity::createAgentRoleJournal)
        .invoke(command);

    return effects().done();
  }

  Effect onEvent(AgentRole.Event.AgentRoleReset event) {
    var agentId = event.agentId();
    var sequence = messageContext().metadata().asCloudEvent().sequence().orElse(Instant.now().toEpochMilli());
    var journalId = agentId + ":" + sequence;

    var command = new AgentRoleJournal.Command.CreateAgentRoleJournal(
        agentId,
        sequence,
        event.systemPrompt(),
        event.resetAt());

    componentClient
        .forEventSourcedEntity(journalId)
        .method(AgentRoleJournalEntity::createAgentRoleJournal)
        .invoke(command);

    return effects().done();
  }
}
