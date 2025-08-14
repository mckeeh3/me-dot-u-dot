package com.example.application;

import static akka.Done.done;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import com.example.domain.PlaybookJournal;

@ComponentId("playbook-journal-entity")
public class PlaybookJournalEntity extends EventSourcedEntity<PlaybookJournal.State, PlaybookJournal.Event> {
  final Logger log = LoggerFactory.getLogger(getClass());
  final String entityId;

  public PlaybookJournalEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public PlaybookJournal.State emptyState() {
    return PlaybookJournal.State.empty();
  }

  public Effect<Done> createPlaybookJournal(PlaybookJournal.Command.CreatePlaybookJournal command) {
    log.debug("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command).stream().toList())
        .thenReply(newState -> done());
  }

  public Effect<PlaybookJournal.State> getState() {
    log.debug("EntityId: {}\n_State: {}", entityId, currentState());

    return effects().reply(currentState());
  }

  @Override
  public PlaybookJournal.State applyEvent(PlaybookJournal.Event event) {
    log.debug("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);

    return switch (event) {
      case PlaybookJournal.Event.PlaybookJournalCreated e -> currentState().onEvent(e);
    };
  }
}
