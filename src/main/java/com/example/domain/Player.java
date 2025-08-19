package com.example.domain;

public interface Player {

  public enum PlayerType {
    human,
    agent
  }

  public record State(String id, PlayerType type, String name, String model) {
    public static State empty() {
      return new State("", PlayerType.human, "", "");
    }

    public boolean isEmpty() {
      return id.isEmpty();
    }

    public State onCommand(Command.CreatePlayer command) {
      if (isEmpty()) {
        return new State(command.id, command.type, command.name, command.model);
      }
      return new State(id, type, command.name, command.model);
    }

    public State onCommand(Command.UpdatePlayer command) {
      if (!isEmpty()) {
        return new State(id, type, command.name, command.model);
      }
      return this;
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
    public record CreatePlayer(String id, PlayerType type, String name, String model) implements Command {}

    public record UpdatePlayer(String id, String name, String model) implements Command {}
  }

}
