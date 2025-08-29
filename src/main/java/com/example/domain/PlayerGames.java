package com.example.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import akka.javasdk.annotations.TypeName;

public interface PlayerGames {

  public record State(
      String branchId,
      String playerId,
      Optional<String> parentBranchId,
      List<Branch> subBranches,
      List<Leaf> leaves,
      Instant updatedAt) {

    public static State empty() {
      return new State("", "", Optional.empty(), List.of(), List.of(), Instant.now());
    }

    public boolean isEmpty() {
      return playerId.isEmpty();
    }

    // ============================================================
    // Command AddGame
    // ============================================================
    public Optional<Event> onCommand(Command.AddGame command) {
      var branchId = playerId; // the tree truck Id is the player Id
      return onCommand(new Command.AddGameToBranch(
          branchId,
          command.playerId,
          command.gameId,
          Optional.empty(),
          command.stats));
    }

    // ============================================================
    // Command AddGameToBranch
    // ============================================================
    public Optional<Event> onCommand(Command.AddGameToBranch command) {
      if (isEmpty()) {
        return handleEmptyBranch(command);
      }

      if (leavesContainsLeaf(command.branchId, command.gameId)) {
        return Optional.empty();
      }

      if (leaves.size() < maxLeaves) {
        return handleAddLeafToBranch(command);
      }

      return handleDelegatedGameToSubBranch(command);
    }

    Optional<Event> handleEmptyBranch(Command.AddGameToBranch command) {
      var leaf = new Leaf(
          command.playerId,
          command.gameId,
          command.stats,
          Instant.now());

      var newLeaves = List.<Leaf>of(leaf);
      var newSubBranches = Stream.generate(Branch::initWithRandomId)
          .limit(maxBranches)
          .toList();

      var stats = reduceStats(newSubBranches, newLeaves);

      return Optional.of(new Event.GameAdded(
          command.branchId,
          command.playerId,
          command.gameId,
          command.parentBranchId,
          newSubBranches,
          newLeaves,
          stats,
          Instant.now()));
    }

    Optional<Event> handleAddLeafToBranch(Command.AddGameToBranch command) {
      var leaf = new Leaf(
          command.playerId,
          command.gameId,
          command.stats,
          Instant.now());

      var newLeaves = Stream.concat(leaves.stream(), Stream.of(leaf)).toList();
      var newSubBranches = subBranches.size() != 0
          ? subBranches
          : Stream.generate(Branch::initWithRandomId)
              .limit(maxBranches)
              .toList();

      var stats = reduceStats(newSubBranches, newLeaves);

      return Optional.of(new Event.GameAdded(
          command.branchId,
          command.playerId,
          command.gameId,
          command.parentBranchId,
          newSubBranches,
          newLeaves,
          stats,
          Instant.now()));
    }

    Optional<Event> handleDelegatedGameToSubBranch(Command.AddGameToBranch command) {
      var subBranchId = selectSubBranchId(command);
      return Optional.of(new Event.DelegatedGameToSubBranch(
          subBranchId,
          command.playerId,
          command.gameId,
          Optional.of(command.branchId),
          command.stats,
          Instant.now()));
    }

    // ============================================================
    // Command UpdateStats
    // ============================================================
    public Optional<Event> onCommand(Command.UpdateStats command) {
      var newSubBranch = new Branch(command.subBranchId, command.stats);
      var newSubBranches = subBranches.stream().map(branch -> {
        if (branch.branchId.equals(command.subBranchId)) {
          return newSubBranch;
        }
        return branch;
      }).toList();

      return Optional.of(new Event.StatsUpdated(
          command.branchId,
          parentBranchId,
          command.subBranchId,
          newSubBranches,
          Instant.now()));
    }

    // ============================================================
    // Utility methods
    // ============================================================
    String selectSubBranchId(Command.AddGameToBranch command) {
      var subBranchId = Math.abs(hash(command.branchId, command.playerId, command.gameId)) % subBranches.size();
      return subBranches.get(subBranchId).branchId;
    }

    public boolean leavesContainsLeaf(String playerId, String gameId) {
      return leaves.stream().anyMatch(leaf -> leaf.playerId.equals(playerId) && leaf.gameId.equals(gameId));
    }

    public GameStats reduceStats() {
      return reduceStats(subBranches, leaves);
    }

    public GameStats reduceStats(List<Branch> branches, List<Leaf> leaves) {
      var leafStats = leaves.stream().map(Leaf::stats).reduce(GameStats::add).orElse(GameStats.empty());
      var branchStats = branches.stream().map(Branch::stats).reduce(GameStats::add).orElse(GameStats.empty());
      return leafStats.add(branchStats);
    }

    // ============================================================
    // Event handlers
    // ============================================================
    public State onEvent(Event.GameAdded event) {
      return new State(
          event.branchId,
          event.playerId,
          event.parentBranchId,
          event.subBranches,
          event.leaves,
          event.updatedAt);
    }

    public State onEvent(Event.DelegatedGameToSubBranch event) {
      return this;
    }

    public State onEvent(Event.StatsUpdated event) {
      return new State(
          branchId,
          playerId,
          parentBranchId,
          event.branches,
          leaves,
          event.updatedAt);
    }
  }

  // ============================================================
  // Commands
  // ============================================================
  public sealed interface Command {
    record AddGame(
        String playerId,
        String gameId,
        GameStats stats) implements Command {}

    record AddGameToBranch(
        String branchId,
        String playerId,
        String gameId,
        Optional<String> parentBranchId,
        GameStats stats) implements Command {}

    record UpdateStats(
        String branchId,
        String subBranchId,
        GameStats stats) implements Command {}
  }

  // ============================================================
  // Events
  // ============================================================
  public sealed interface Event {

    @TypeName("game-added")
    record GameAdded(
        String branchId,
        String playerId,
        String gameId,
        Optional<String> parentBranchId,
        List<Branch> subBranches,
        List<Leaf> leaves,
        GameStats stats,
        Instant updatedAt) implements Event {}

    @TypeName("delegated-game-to-sub-branch")
    record DelegatedGameToSubBranch(
        String subBranchId,
        String playerId,
        String gameId,
        Optional<String> parentBranchId,
        GameStats stats,
        Instant updatedAt) implements Event {}

    @TypeName("stats-updated")
    record StatsUpdated(
        String branchId,
        Optional<String> parentBranchId,
        String updatedSubBranchId,
        List<Branch> branches,
        Instant updatedAt) implements Event {}
  }

  // ============================================================
  // Utility methods
  // ============================================================

  static String generateRandomId() {
    return generateRandomId(6);
  }

  static int hash(String branchId, String playerId, String gameId) {
    return (int) Murmur1.hash(branchId + playerId + gameId);
  }

  private static String generateRandomId(int length) {
    var chars = "0123456789abcdefghijklmnopqrstuvwxyz";
    var sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      var randomIndex = (int) (Math.random() * chars.length());
      sb.append(chars.charAt(randomIndex));
    }
    return sb.toString();
  }

  // ============================================================
  // Utility records
  // ============================================================
  public record GameStats(int gamesPlayed, int gamesWon, int gamesLost, int gamesDraw) {
    public static GameStats empty() {
      return new GameStats(0, 0, 0, 0);
    }

    public static GameStats playerWins() {
      return new GameStats(1, 1, 0, 0);
    }

    public static GameStats playerLoses() {
      return new GameStats(1, 0, 1, 0);
    }

    public static GameStats playerDraws() {
      return new GameStats(1, 0, 0, 1);
    }

    public GameStats add(GameStats other) {
      return new GameStats(
          gamesPlayed + other.gamesPlayed,
          gamesWon + other.gamesWon,
          gamesLost + other.gamesLost,
          gamesDraw + other.gamesDraw);
    }
  }

  public static int maxBranches = 10;

  public record Branch(String branchId, GameStats stats) {
    public static Branch empty() {
      return new Branch("", GameStats.empty());
    }

    public static Branch initWithRandomId() {
      return new Branch(generateRandomId(), GameStats.empty());
    }
  }

  public static int maxLeaves = 10;

  public record Leaf(String playerId, String gameId, GameStats stats, Instant createdAt) {
    public static Leaf empty() {
      return new Leaf("", "", GameStats.empty(), Instant.now());
    }
  }
}
