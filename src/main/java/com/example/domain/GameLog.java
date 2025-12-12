package com.example.domain;

import java.time.Instant;
import java.util.Optional;

import akka.javasdk.annotations.TypeName;

public interface GameLog {

  public record State(
      String gameId,
      String agentId,
      int moveNumber,
      Instant createdAt,
      String response) {

    public static State empty() {
      return new State("", "", 0, Instant.now(), "");
    }

    public boolean isEmpty() {
      return gameId.isEmpty();
    }

    public Optional<Event> onCommand(Command.CreateMakeMoveResponse command) {
      if (!isEmpty()) {
        return Optional.empty();
      }

      var newCreatedAt = Instant.now();
      return Optional.of(new Event.MakeMoveResponseCreated(
          command.gameId,
          command.agentId,
          command.moveNumber,
          newCreatedAt,
          command.response));
    }

    public State onEvent(Event.MakeMoveResponseCreated event) {
      return new State(
          event.gameId,
          event.agentId,
          event.moveNumber,
          event.createdAt,
          event.response);
    }

    public static String entityIdFrom(String gameId, String agentId, int moveNumber) {
      return "%s-%s-%d".formatted(gameId, agentId, moveNumber);
    }
  }

  public sealed interface Command {
    record CreateMakeMoveResponse(
        String gameId,
        String agentId,
        int moveNumber,
        String response) implements Command {}
  }

  public sealed interface Event {
    @TypeName("make-move-response-created")
    record MakeMoveResponseCreated(
        String gameId,
        String agentId,
        int moveNumber,
        Instant createdAt,
        String response) implements Event {}
  }
}
