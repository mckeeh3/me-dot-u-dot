package com.example.domain;

import java.time.Instant;

public interface GameActionLog {

  public enum Type {
    empty,
    make_move,
    tool_call,
    agent_response,
    forfeit_move
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
      return playerId.isEmpty();
    }

    public State onCommand(Command.CreateAgentLog command) {
      if (isEmpty()) {
        return new State(command.id, command.type, command.time, command.playerId, command.gameId, command.message);
      }
      return this;
    }

    public static Command.CreateAgentLog log(Type type, String playerId, String gameId, String message) {
      var id = "%s-%s-%l".formatted(playerId, gameId, System.currentTimeMillis());
      var time = Instant.now();
      return new Command.CreateAgentLog(id, type, time, playerId, gameId, message);
    }
  }

  public sealed interface Command {
    record CreateAgentLog(String id, Type type, Instant time, String playerId, String gameId, String message) implements Command {}
  }
}
