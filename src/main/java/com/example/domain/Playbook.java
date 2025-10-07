package com.example.domain;

import java.time.Instant;
import java.util.Optional;

public interface Playbook {

  public record State(
      String agentId,
      String instructions,
      Instant updatedAt) {

    public static State empty() {
      return new State("", "", Instant.now());
    }

    public boolean isEmpty() {
      return agentId.isEmpty();
    }

    public Optional<Event> onCommand(Command.WritePlaybook command) {
      return Optional.of(new Event.PlaybookUpdated(command.agentId, command.instructions, Instant.now()));
    }

    public State onEvent(Event.PlaybookUpdated event) {
      return new State(event.agentId, event.instructions, event.updatedAt);
    }
  }

  public sealed interface Command {
    record WritePlaybook(String agentId, String instructions) implements Command {}
  }

  public sealed interface Event {
    record PlaybookUpdated(String agentId, String instructions, Instant updatedAt) implements Event {}
  }
}
