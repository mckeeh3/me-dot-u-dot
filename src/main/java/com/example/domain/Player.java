package com.example.domain;

import akka.javasdk.annotations.TypeName;

public interface Player {

  public enum PlayerType {
    human,
    agent
  }

  public record State(String id, PlayerType type, String name) {
    public static State empty() {
      return new State("", PlayerType.human, "");
    }

    public boolean isEmpty() {
      return id.isEmpty();
    }

    public State onCommand(Command.CreatePlayer command) {
      return new State(command.id, command.type, command.name);
    }

    public State onCommand(Command.UpdatePlayer command) {
      return new State(command.id, type, command.name);
    }

    public State onEvent(Event.PlayerCreated event) {
      return new State(event.id, event.type, event.name);
    }

    public State onEvent(Event.PlayerUpdated event) {
      return new State(id, type, event.name);
    }

    public boolean isAgent() {
      return type == PlayerType.agent;
    }

    public boolean isHuman() {
      return type == PlayerType.human;
    }
  }

  // ============================================================
  // Commands
  // ============================================================
  public sealed interface Command {
    public record CreatePlayer(String id, PlayerType type, String name) implements Command {}

    public record UpdatePlayer(String id, String name) implements Command {}
  }

  // ============================================================
  // Events
  // ============================================================
  public sealed interface Event {
    @TypeName("player-created")
    public record PlayerCreated(String id, PlayerType type, String name) implements Event {}

    @TypeName("player-updated")
    public record PlayerUpdated(String id, String name) implements Event {}
  }
}
