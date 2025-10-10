package com.example.application;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.Playbook;
import com.example.domain.PlaybookJournal;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;

@Component(id = "playbook-to-playbook-journal-consumer")
@Consume.FromEventSourcedEntity(PlaybookEntity.class)
public class PlaybookToPlaybookJournalConsumer extends Consumer {
  final Logger log = LoggerFactory.getLogger(getClass());
  final ComponentClient componentClient;

  public PlaybookToPlaybookJournalConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onEvent(Playbook.Event event) {
    log.debug("Event: {}", event);

    return switch (event) {
      case Playbook.Event.PlaybookUpdated e -> onEvent(e);
      default -> {
        log.debug("Unknown event: {}", event);
        yield effects().done();
      }
    };
  }

  Effect onEvent(Playbook.Event.PlaybookUpdated event) {
    var agentId = event.agentId();
    var sequence = messageContext().metadata().asCloudEvent().sequence().orElse(Instant.now().toEpochMilli());
    var journalId = agentId + ":" + sequence;

    var command = new PlaybookJournal.Command.CreatePlaybookJournal(
        agentId,
        sequence,
        event.instructions(),
        event.updatedAt());

    componentClient
        .forEventSourcedEntity(journalId)
        .method(PlaybookJournalEntity::createPlaybookJournal)
        .invoke(command);

    return effects().done();
  }
}
