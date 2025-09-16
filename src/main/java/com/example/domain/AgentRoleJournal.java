package com.example.domain;

import java.time.Instant;
import java.util.Optional;

public interface AgentRoleJournal {

  public record State(
      String journalId,
      String agentId,
      long sequenceId,
      String systemPrompt,
      Instant updatedAt) {

    public static State empty() {
      return new State("", "", 0, "", Instant.now());
    }

    public boolean isEmpty() {
      return agentId.isEmpty();
    }

    public Optional<Event> onCommand(Command.CreateAgentRoleJournal command) {
      if (!isEmpty()) {
        return Optional.empty();
      }

      var journalId = command.agentId + ":" + command.sequenceId;

      return Optional.of(new Event.AgentRoleJournalCreated(
          journalId,
          command.agentId,
          command.sequenceId,
          command.systemPrompt,
          command.updatedAt));
    }

    public State onEvent(Event.AgentRoleJournalCreated event) {
      return new State(
          event.journalId,
          event.agentId,
          event.sequenceId,
          event.systemPrompt,
          event.updatedAt);
    }
  }

  public sealed interface Command {
    record CreateAgentRoleJournal(
        String agentId,
        long sequenceId,
        String systemPrompt,
        Instant updatedAt) implements Command {}
  }

  public sealed interface Event {
    record AgentRoleJournalCreated(
        String journalId,
        String agentId,
        long sequenceId,
        String systemPrompt,
        Instant updatedAt) implements Event {}
  }
}
