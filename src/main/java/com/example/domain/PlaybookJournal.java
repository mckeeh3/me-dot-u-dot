package com.example.domain;

import java.time.Instant;
import java.util.Optional;

public interface PlaybookJournal {

  public record State(
      String journalId,
      String agentId,
      long sequenceId,
      String instructions,
      Instant updatedAt) {

    public static State empty() {
      return new State("", "", 0, "", Instant.now());
    }

    public boolean isEmpty() {
      return agentId.isEmpty();
    }

    public Optional<Event> onCommand(Command.CreatePlaybookJournal command) {
      if (!isEmpty()) {
        return Optional.empty();
      }

      var journalId = command.agentId + ":" + command.sequenceId;

      return Optional.of(new Event.PlaybookJournalCreated(
          journalId,
          command.agentId,
          command.sequenceId,
          command.instructions,
          command.updatedAt));
    }

    public State onEvent(Event.PlaybookJournalCreated event) {
      return new State(
          event.journalId,
          event.agentId,
          event.sequenceId,
          event.instructions,
          event.updatedAt);
    }
  }

  public sealed interface Command {
    record CreatePlaybookJournal(
        String agentId,
        long sequenceId,
        String instructions,
        Instant updatedAt) implements Command {}
  }

  public sealed interface Event {
    record PlaybookJournalCreated(
        String journalId,
        String agentId,
        long sequenceId,
        String instructions,
        Instant updatedAt) implements Event {}
  }
}
