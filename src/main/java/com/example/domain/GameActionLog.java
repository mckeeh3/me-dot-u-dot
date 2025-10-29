package com.example.domain;

import java.time.Instant;

public interface GameActionLog {

  public enum Type {
    empty,
    game_created,
    make_move,
    tool_call,
    model_prompt,
    model_response,
    forfeit_move,
    guardrail_event,
    game_finished,
    game_canceled
  }

  public record State(
      String id,
      Type type,
      Instant time,
      String playerId,
      String gameId,
      String message) {

    public static State empty() {
      return new State("", Type.empty, Instant.now(), "", "", "");
    }

    public boolean isEmpty() {
      return id.isEmpty();
    }

    public State onCommand(Command.CreateAgentLog command) {
      if (isEmpty()) {
        return new State(command.id, command.type, command.time, command.playerId, command.gameId, command.message);
      }
      return this;
    }

    static String idFrom(String gameId, String playerId) {
      return "%s-%s-%d".formatted(gameId, playerId, System.currentTimeMillis());
    }

    public static Command.CreateAgentLog log(Type type, String playerId, String gameId, String message) {
      var id = idFrom(gameId, playerId);
      var time = Instant.now();
      return new Command.CreateAgentLog(id, type, time, playerId, gameId, message);
    }

    public static Command.CreateAgentLog log(Type type, Instant time, String playerId, String gameId, String message) {
      var id = idFrom(gameId, playerId);
      return new Command.CreateAgentLog(id, type, time, playerId, gameId, message);
    }
  }

  public sealed interface Command {
    record CreateAgentLog(String id, Type type, Instant time, String playerId, String gameId, String message) implements Command {}
  }
}
