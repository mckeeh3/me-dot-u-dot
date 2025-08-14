package com.example.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import akka.javasdk.annotations.TypeName;

public interface DotGame {

  public enum Status {
    empty,
    in_progress,
    won_by_player,
    draw
  }

  public record State(
      String gameId,
      Instant createdAt,
      Board board,
      Status status,
      PlayerStatus player1Status,
      PlayerStatus player2Status,
      Optional<PlayerStatus> currentPlayer,
      List<Move> moveHistory) {

    public static State empty() {
      return new State("", Instant.now(), Board.empty(), Status.empty, PlayerStatus.empty(), PlayerStatus.empty(), Optional.empty(), List.of());
    }

    public boolean isEmpty() {
      return gameId.isEmpty();
    }

    // ============================================================
    // Command CreateGame
    // ============================================================
    public Optional<Event> onCommand(Command.CreateGame command) {
      if (!isEmpty()) {
        return Optional.empty();
      }

      return Optional.of(
          new Event.GameCreated(
              command.gameId,
              Instant.now(),
              Board.of(command.level),
              Status.in_progress,
              new PlayerStatus(command.player1, 0, 0, false),
              new PlayerStatus(command.player2, 0, 0, false),
              Optional.of(new PlayerStatus(command.player1, 0, 0, false)),
              command.level,
              List.of()));
    }

    // ============================================================
    // Command MakeMove
    // ============================================================
    public List<Event> onCommand(Command.MakeMove command) {
      if (!status.equals(Status.in_progress)) {
        return List.of();
      }

      var dotOptional = board.dotAt(command.dotId);
      if (dotOptional.isEmpty()) {
        return List.of();
      }

      var dot = dotOptional.get();
      if (isEmpty() || isGameOver() || dot.isOccupied()) {
        return List.of();
      }

      // Check if it's the player's turn
      if (currentPlayer.isEmpty() || !command.playerId.equals(currentPlayer.get().player().id())) {
        return List.of();
      }

      var newBoard = board.withDot(command.dotId, currentPlayer.get().player());

      var newPlayer1Status = isPlayer1Turn() ? player1Status.incrementMoves() : player1Status;
      newPlayer1Status = isPlayer1Turn() ? newPlayer1Status.incrementScore(newBoard.scoreDotAt(command.dotId)) : newPlayer1Status;
      var newPlayer2Status = isPlayer2Turn() ? player2Status.incrementMoves() : player2Status;
      newPlayer2Status = isPlayer2Turn() ? newPlayer2Status.incrementScore(newBoard.scoreDotAt(command.dotId)) : newPlayer2Status;

      var newStatus = gameStatus(newBoard, newPlayer1Status, newPlayer2Status);

      if (!newStatus.equals(Status.in_progress)) {
        if (newStatus == Status.won_by_player) {
          newPlayer1Status = isCurrentPlayer(newPlayer1Status) ? newPlayer1Status.setWinner() : newPlayer1Status.setLoser();
          newPlayer2Status = isCurrentPlayer(newPlayer2Status) ? newPlayer2Status.setWinner() : newPlayer2Status.setLoser();
        }
      }

      var newMoveHistory = Stream.concat(moveHistory.stream(), Stream.of(new Move(command.dotId, command.playerId)))
          .toList();

      var newCurrentPlayer = getNextPlayer();

      var moveMadeEvent = new Event.MoveMade(
          gameId,
          newBoard,
          newStatus,
          newPlayer1Status,
          newPlayer2Status,
          Optional.of(newCurrentPlayer),
          newMoveHistory,
          Instant.now());

      if (!newStatus.equals(Status.in_progress)) {
        if (newStatus == Status.won_by_player) {
          newPlayer1Status = isCurrentPlayer(newPlayer1Status) ? newPlayer1Status.setWinner() : newPlayer1Status.setLoser();
          newPlayer2Status = isCurrentPlayer(newPlayer2Status) ? newPlayer2Status.setWinner() : newPlayer2Status.setLoser();
        }

        var winningPlayerStatus = Optional.<PlayerStatus>empty();
        if (newStatus == Status.won_by_player) {
          winningPlayerStatus = isPlayer1Turn() ? Optional.of(newPlayer1Status) : Optional.of(newPlayer2Status);
        }

        var gameCompletedEvent = new Event.GameCompleted(
            gameId,
            newStatus,
            newPlayer1Status,
            newPlayer2Status,
            winningPlayerStatus,
            Instant.now());

        return List.of(moveMadeEvent, gameCompletedEvent);
      }

      return List.of(moveMadeEvent);
    }

    // ============================================================
    // Event handlers
    // ============================================================
    public State onEvent(Event.GameCreated event) {
      return new State(
          event.gameId,
          event.createdAt,
          event.board,
          event.status,
          event.player1Status,
          event.player2Status,
          event.currentPlayerStatus,
          event.moveHistory);
    }

    public State onEvent(Event.MoveMade event) {
      return new State(
          gameId,
          createdAt,
          event.board,
          event.status,
          event.player1Status,
          event.player2Status,
          event.currentPlayerStatus,
          event.moveHistory);
    }

    public State onEvent(Event.GameCompleted event) {
      return new State(
          gameId,
          createdAt,
          board,
          event.status,
          event.player1Status,
          event.player2Status,
          Optional.empty(),
          moveHistory);
    }

    boolean isGameOver() {
      return status == Status.won_by_player || status == Status.draw;
    }

    static Status gameStatus(Board board, PlayerStatus player1Status, PlayerStatus player2Status) {
      var winningScore = (board.level.getSize() / 2) + 1;
      if (player1Status.score() >= winningScore) {
        return Status.won_by_player;
      }

      if (player2Status.score() >= winningScore) {
        return Status.won_by_player;
      }

      if (board.dots.stream().allMatch(Dot::isOccupied)) {
        return Status.draw;
      }

      return Status.in_progress;
    }

    boolean isCurrentPlayer(PlayerStatus playerStatus) {
      return isCurrentPlayer(playerStatus.player());
    }

    boolean isCurrentPlayer(Player player) {
      return currentPlayer.isPresent() && currentPlayer.get().player().id().equals(player.id());
    }

    Optional<PlayerStatus> getCurrentPlayer() {
      return currentPlayer;
    }

    String getCurrentPlayerName() {
      return getCurrentPlayer()
          .map(PlayerStatus::player)
          .map(Player::name)
          .orElse("Unknown Player");
    }

    PlayerStatus getNextPlayer() {
      if (currentPlayer.isEmpty()) {
        return player1Status;
      }

      if (currentPlayer.get().player().id().equals(player1Status.player().id())) {
        return player2Status;
      }

      return player1Status;
    }

    boolean isPlayer1Turn() {
      return currentPlayer.isEmpty() || currentPlayer.get().player().id().equals(player1Status.player().id());
    }

    boolean isPlayer2Turn() {
      return !currentPlayer.isEmpty() && currentPlayer.get().player().id().equals(player2Status.player().id());
    }
  }

  // ============================================================
  // Commands
  // ============================================================
  public sealed interface Command {

    public record CreateGame(
        String gameId,
        Player player1,
        Player player2,
        Board.Level level) implements Command {}

    public record MakeMove(
        String gameId,
        String playerId,
        String dotId) implements Command {}

    public record CompleteGame(
        String gameId,
        String winner) implements Command {}
  }

  // ============================================================
  // Events
  // ============================================================
  public sealed interface Event {

    @TypeName("game-created")
    public record GameCreated(
        String gameId,
        Instant createdAt,
        Board board,
        Status status,
        PlayerStatus player1Status,
        PlayerStatus player2Status,
        Optional<PlayerStatus> currentPlayerStatus,
        Board.Level level,
        List<Move> moveHistory) implements Event {}

    @TypeName("move-made")
    public record MoveMade(
        String gameId,
        Board board,
        Status status,
        PlayerStatus player1Status,
        PlayerStatus player2Status,
        Optional<PlayerStatus> currentPlayerStatus,
        List<Move> moveHistory,
        Instant timestamp) implements Event {}

    @TypeName("game-completed")
    public record GameCompleted(
        String gameId,
        Status status,
        PlayerStatus player1Status,
        PlayerStatus player2Status,
        Optional<PlayerStatus> winningPlayerStatus,
        Instant timestamp) implements Event {}
  }

  // ============================================================
  // Player
  // ============================================================
  public enum PlayerType {
    human,
    agent
  }

  public record Player(String id, PlayerType type, String name) {
    static Player empty() {
      return new Player("", PlayerType.human, "");
    }

    public boolean isAgent() {
      return type == PlayerType.agent;
    }

    public boolean isHuman() {
      return type == PlayerType.human;
    }
  }

  // ============================================================
  // PlayerStatus
  // ============================================================
  public record PlayerStatus(Player player, int moves, int score, boolean isWinner) {
    static PlayerStatus empty() {
      return new PlayerStatus(Player.empty(), 0, 0, false);
    }

    PlayerStatus incrementMoves() {
      return new PlayerStatus(player, moves + 1, score, isWinner);
    }

    PlayerStatus incrementScore(int scoreIncrement) {
      return new PlayerStatus(player, moves, score + scoreIncrement, isWinner);
    }

    PlayerStatus setWinner() {
      return new PlayerStatus(player, moves, score, true);
    }

    PlayerStatus setLoser() {
      return new PlayerStatus(player, moves, score, false);
    }
  }

  // ============================================================
  // Dot in the board
  // ============================================================
  public record Dot(String id, Optional<Player> player) {
    boolean isOccupied() {
      return player.isPresent();
    }

    boolean isEmpty() {
      return !isOccupied();
    }

    public Dot(String id) {
      this(id, Optional.empty());
    }

    Dot withPlayer(Player player) {
      return new Dot(id, Optional.of(player));
    }
  }

  // ============================================================
  // Board in the game
  // ============================================================
  public record Board(Level level, List<Dot> dots) {
    static Board empty() {
      return new Board(Level.one, List.of());
    }

    public enum Level {
      one,
      two,
      three,
      four,
      five,
      six,
      seven,
      eight,
      nine;

      public int getSize() {
        return switch (this) {
          case one -> 5;
          case two -> 7;
          case three -> 9;
          case four -> 11;
          case five -> 13;
          case six -> 15;
          case seven -> 17;
          case eight -> 19;
          case nine -> 21;
        };
      }
    }

    static Board of(Level level) {
      var size = level.getSize();

      return new Board(level, IntStream.range(0, size)
          .boxed()
          .flatMap(row -> IntStream.range(0, size)
              .mapToObj(col -> {
                char rowChar = (char) ('A' + row);
                int colNum = col + 1;
                return new Dot(rowChar + String.valueOf(colNum));
              }))
          .toList());
    }

    public Optional<Dot> dotAt(String id) {
      return dots.stream()
          .filter(dot -> dot.id().equals(id))
          .findFirst();
    }

    Board withDot(String id, Player player) {
      return new Board(level, dots.stream()
          .map(dot -> dot.id().equals(id) ? dot.withPlayer(player) : dot)
          .toList());
    }

    public int scoreDotAt(String id) {
      var dot = dotAt(id);
      if (dot.isEmpty() || dot.get().player().isEmpty()) {
        return 0; // No player at this dot
      }

      var player = dot.get().player().get();
      var level = getLevel();
      var requiredLength = (level / 2) + 1;

      int totalScore = 0;

      // Check all 8 directions individually
      var directions = List.of(
          Direction.horizontal(), // Right (increase row: A3->B3->C3)
          Direction.vertical(), // Down (increase column: C1->C2->C3)
          Direction.diagonalPositive(), // Down-right (increase both: A1->B2->C3)
          Direction.diagonalNegative() // Down-left (increase row, decrease column: E1->D2->C3)
      );

      for (var direction : directions) {
        if (hasValidLine(id, player, direction, requiredLength)) {
          totalScore++;
        }
      }

      return totalScore;
    }

    boolean hasValidLine(String startId, Player player, Direction direction, int requiredLength) {
      var coords = parseCoordinates(startId);
      if (coords == null) {
        return false;
      }

      // Count dots in the specified direction (including center)
      var countPositive = countConsecutiveDots(coords, direction, player);
      var countNegative = countConsecutiveDots(coords, direction.negate(), player);
      var count = 1 + countPositive + countNegative; // new dot + dots in the positive direction + dots in the negative direction

      return count >= requiredLength;
    }

    int countConsecutiveDots(Coordinates coords, Direction direction, Player player) {
      var count = 0;
      var current = coords;

      while (true) {
        current = current.move(direction);
        if (current == null || !isValidCoordinates(current)) {
          break;
        }

        var dot = dotAt(current.toString());
        if (dot.isEmpty() || dot.get().player().isEmpty() || !dot.get().player().get().id().equals(player.id())) {
          break;
        }

        count++;
      }

      return count;
    }

    boolean isValidCoordinates(Coordinates coords) {
      return coords.row >= 0 && coords.row < getLevel() &&
          coords.col >= 0 && coords.col < getLevel();
    }

    int getLevel() {
      return level.getSize();
    }

    Coordinates parseCoordinates(String id) {
      if (id.length() < 2)
        return null;

      char rowChar = id.charAt(0);
      try {
        int col = Integer.parseInt(id.substring(1));
        int row = rowChar - 'A';
        return new Coordinates(row, col - 1); // Convert to 0-based
      } catch (NumberFormatException e) {
        return null;
      }
    }

    record Coordinates(int row, int col) {
      Coordinates move(Direction direction) {
        // For the dot game, rowDelta affects the letter (A-E), colDelta affects the number (1-5)
        return new Coordinates(row + direction.rowDelta, col + direction.colDelta);
      }

      @Override
      public String toString() {
        char rowChar = (char) ('A' + row);
        return rowChar + String.valueOf(col + 1);
      }
    }

    record Direction(int rowDelta, int colDelta) {
      static Direction horizontal() {
        return new Direction(1, 0);
      }

      static Direction vertical() {
        return new Direction(0, 1);
      }

      static Direction diagonalPositive() { // positive means positive row and positive column
        return new Direction(1, 1);
      }

      static Direction diagonalNegative() {
        return new Direction(-1, 1);
      }

      Direction negate() {
        return new Direction(-rowDelta, -colDelta);
      }
    }
  }

  public record Move(String dotId, String playerId) {}
}
