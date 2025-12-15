package com.example.domain;

import java.time.Instant;
import java.util.Optional;

import akka.javasdk.annotations.TypeName;

public interface GameMoveLog {

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

    public static String entityIdFrom(String gameId, String agentId, int moveNumber) {
      return "%s-%s-%d".formatted(gameId, agentId, moveNumber);
    }

    public Optional<Event> onCommand(Command.CreateGameMoveLog command) {
      if (!isEmpty()) {
        return Optional.empty();
      }

      var newCreatedAt = Instant.now();
      return Optional.of(new Event.GameMoveLogCreated(
          command.gameId,
          command.agentId,
          command.moveNumber,
          newCreatedAt,
          command.response));
    }

    public State onEvent(Event.GameMoveLogCreated event) {
      return new State(
          event.gameId,
          event.agentId,
          event.moveNumber,
          event.createdAt,
          event.response);
    }
  }

  public sealed interface Command {
    record CreateGameMoveLog(
        String gameId,
        String agentId,
        int moveNumber,
        String response) implements Command {}
  }

  public sealed interface Event {
    @TypeName("game-move-log-created")
    record GameMoveLogCreated(
        String gameId,
        String agentId,
        int moveNumber,
        Instant createdAt,
        String response) implements Event {}
  }
}
