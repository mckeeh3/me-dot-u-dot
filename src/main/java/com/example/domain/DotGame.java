package com.example.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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
    draw,
    canceled
  }

  public record State(
      String gameId,
      Status status,
      Instant createdAt,
      Instant updatedAt,
      Instant turnCompletedAt,
      Optional<Instant> finishedAt,
      PlayerStatus player1Status,
      PlayerStatus player2Status,
      Optional<PlayerStatus> currentPlayerStatus,
      List<Move> moveHistory,
      Board board) {

    public static State empty() {
      return new State(
          "",
          Status.empty,
          Instant.now(),
          Instant.now(),
          Instant.now(),
          Optional.empty(),
          PlayerStatus.empty(),
          PlayerStatus.empty(),
          Optional.empty(),
          List.of(),
          Board.empty());
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
              Status.in_progress,
              Instant.now(),
              Instant.now(),
              Instant.now(),
              Optional.empty(),
              new PlayerStatus(command.player1, 0, 0, false),
              new PlayerStatus(command.player2, 0, 0, false),
              Optional.of(new PlayerStatus(command.player1, 0, 0, false)),
              List.of(),
              command.level,
              Board.of(command.level)));
    }

    // ============================================================
    // Command MakeMove
    // ============================================================
    public List<Event> onCommand(Command.MakeMove command) {
      if (!status.equals(Status.in_progress)) {
        return List.of(); // No state change tells the agent to try again
      }

      var squareOptional = board.squareAt(command.squareId);
      if (squareOptional.isEmpty()) {
        var message = "Invalid board position: %s".formatted(command.squareId);
        return forfeitMove(command.playerId, message); // invalid board position
      }

      var square = squareOptional.get();
      if (square.isOccupied()) {
        return List.of(); // No state change tells the agent to try again
      }

      if (currentPlayerStatus.isEmpty() || !command.playerId.equals(currentPlayerStatus.get().player().id())) {
        return List.of(); // No state change tells the agent to try again
      }

      var newBoard = board.withSquare(command.squareId, currentPlayerStatus.get().player());

      var newPlayer1Status = isPlayer1Turn() ? player1Status.makeMove(command.squareId, board.level(), moveHistory) : player1Status;
      var newPlayer2Status = isPlayer2Turn() ? player2Status.makeMove(command.squareId, board.level(), moveHistory) : player2Status;

      var newStatus = gameStatus(newBoard, newPlayer1Status, newPlayer2Status);

      if (!newStatus.equals(Status.in_progress)) {
        if (newStatus == Status.won_by_player) {
          newPlayer1Status = isCurrentPlayer(newPlayer1Status) ? newPlayer1Status.setWinner() : newPlayer1Status.setLoser();
          newPlayer2Status = isCurrentPlayer(newPlayer2Status) ? newPlayer2Status.setWinner() : newPlayer2Status.setLoser();
        } else { // draw
          newPlayer1Status = newPlayer1Status.setLoser();
          newPlayer2Status = newPlayer2Status.setLoser();
        }
      }

      var thinkMs = Duration.between(turnCompletedAt, Instant.now()).toMillis();
      var newMove = new Move(command.squareId, command.playerId, thinkMs);
      var newMoveHistory = Stream.concat(moveHistory.stream(), Stream.of(newMove))
          .toList();

      var newCurrentPlayer = newStatus == Status.in_progress ? Optional.of(getNextPlayer()) : Optional.<PlayerStatus>empty();
      var newUpdatedAt = Instant.now();

      var madeMoveEvent = new Event.MoveMade(
          gameId,
          newStatus,
          newUpdatedAt,
          newPlayer1Status,
          newPlayer2Status,
          newCurrentPlayer,
          newMoveHistory,
          newBoard);

      if (newStatus != Status.in_progress) {
        var eventGameFinished = new Event.GameFinished(gameId, newStatus, Instant.now(), Optional.of(Instant.now()));
        var eventGameResults = new Event.GameResults(gameId, newStatus, Instant.now(), newPlayer1Status, newPlayer2Status);

        if (isHumanPlayer(command.playerId, this)) {
          var newTurnCompletedAt = Instant.now();
          return List.of(madeMoveEvent, eventGameFinished, eventGameResults,
              new Event.PlayerTurnCompleted(
                  madeMoveEvent.gameId,
                  madeMoveEvent.status,
                  newTurnCompletedAt,
                  newPlayer1Status,
                  newPlayer2Status,
                  newCurrentPlayer,
                  madeMoveEvent.moveHistory));
        }

        return List.of(madeMoveEvent, eventGameFinished, eventGameResults);
      }

      if (isHumanPlayer(command.playerId, this)) {
        var newTurnCompletedAt = Instant.now();
        return List.of(madeMoveEvent,
            new Event.PlayerTurnCompleted(
                madeMoveEvent.gameId,
                madeMoveEvent.status,
                newTurnCompletedAt,
                newPlayer1Status,
                newPlayer2Status,
                newCurrentPlayer,
                madeMoveEvent.moveHistory));
      }

      return List.of(madeMoveEvent);
    }

    List<Event> forfeitMove(String playerId, String message) {
      var newUpdatedAt = Instant.now();
      var newTurnCompletedAt = Instant.now();
      var newCurrentPlayer = Optional.of(getNextPlayer());

      return List.of(
          new Event.MoveForfeited(
              gameId,
              status,
              newUpdatedAt,
              newCurrentPlayer,
              message),
          new Event.PlayerTurnCompleted(
              gameId,
              status,
              newTurnCompletedAt,
              player1Status,
              player2Status,
              newCurrentPlayer,
              moveHistory));
    }

    boolean isHumanPlayer(String playerId, State state) {
      var player = playerId.equals(state.player1Status().player().id()) ? state.player1Status() : state.player2Status();
      return player.player().type() == PlayerType.human;
    }

    // ============================================================
    // Command PlayerTurnCompleted
    // ============================================================
    public Event onCommand(Command.PlayerTurnCompleted command) {
      var newTurnCompletedAt = Instant.now();
      var lastMove = moveHistory.get(moveHistory.size() - 1);
      var newMoveHistory = moveHistory;
      if (lastMove.playerId().equals(command.playerId())) {
        newMoveHistory = moveHistory.stream()
            .map(move -> {
              if (move.squareId().equals(lastMove.squareId())) {
                var thinkMs = Duration.between(turnCompletedAt, newTurnCompletedAt).toMillis();
                var newMove = new Move(move.squareId, move.playerId, thinkMs);
                return newMove;
              }
              return move;
            })
            .toList();
      }

      return new Event.PlayerTurnCompleted(
          gameId,
          status,
          newTurnCompletedAt,
          player1Status,
          player2Status,
          currentPlayerStatus,
          newMoveHistory);
    }

    // ============================================================
    // Command ForfeitMove
    // ============================================================
    public List<Event> onCommand(Command.ForfeitMove command) {
      if (!status.equals(Status.in_progress)) {
        return List.of();
      }

      // Check if it's the player's turn
      if (currentPlayerStatus.isEmpty() || !command.playerId.equals(currentPlayerStatus.get().player().id())) {
        return List.of();
      }

      var newUpdatedAt = Instant.now();
      var newTurnCompletedAt = Instant.now();
      var newCurrentPlayer = Optional.of(getNextPlayer());

      return List.of(
          new Event.MoveForfeited(
              gameId,
              status,
              newUpdatedAt,
              newCurrentPlayer,
              command.message),
          new Event.PlayerTurnCompleted(
              gameId,
              status,
              newTurnCompletedAt,
              player1Status,
              player2Status,
              newCurrentPlayer,
              moveHistory));
    }

    // ============================================================
    // Command CancelGame
    // ============================================================
    public Optional<Event> onCommand(Command.CancelGame command) {
      if (!status.equals(Status.in_progress)) {
        return Optional.empty();
      }

      return Optional.of(new Event.GameCanceled(
          gameId,
          Status.canceled,
          Instant.now(),
          Optional.of(Instant.now()),
          player1Status,
          player2Status,
          Optional.empty(),
          command.reason));
    }

    // ============================================================
    // Event handlers
    // ============================================================
    public State onEvent(Event.GameCreated event) {
      return new State(
          event.gameId,
          event.status,
          event.createdAt,
          event.updatedAt,
          event.turnCompletedAt,
          event.finishedAt,
          event.player1Status,
          event.player2Status,
          event.currentPlayerStatus,
          event.moveHistory,
          event.board);
    }

    public State onEvent(Event.MoveMade event) {
      return new State(
          gameId,
          event.status,
          createdAt,
          event.updatedAt,
          turnCompletedAt,
          finishedAt,
          event.player1Status,
          event.player2Status,
          event.currentPlayerStatus,
          event.moveHistory,
          event.board);
    }

    public State onEvent(Event.GameCanceled event) {
      return new State(
          gameId,
          event.status,
          createdAt,
          event.updatedAt,
          turnCompletedAt,
          event.finishedAt,
          player1Status,
          player2Status,
          event.currentPlayerStatus,
          moveHistory,
          board);

    }

    public State onEvent(Event.MoveForfeited event) {
      return new State(
          gameId,
          status,
          createdAt,
          event.updatedAt,
          turnCompletedAt,
          finishedAt,
          player1Status,
          player2Status,
          event.currentPlayerStatus,
          moveHistory,
          board);
    }

    public State onEvent(Event.PlayerTurnCompleted event) {
      return new State(
          gameId,
          status,
          createdAt,
          updatedAt,
          event.turnCompletedAt,
          finishedAt,
          player1Status,
          player2Status,
          currentPlayerStatus,
          event.moveHistory,
          board);
    }

    public State onEvent(Event.GameFinished event) {
      return new State(
          gameId,
          status,
          createdAt,
          event.updatedAt,
          turnCompletedAt,
          event.finishedAt,
          player1Status,
          player2Status,
          Optional.empty(),
          moveHistory,
          board);
    }

    public State onEvent(Event.GameResults event) {
      return this;
    }

    static Status gameStatus(Board board, PlayerStatus player1Status, PlayerStatus player2Status) {
      var winningScore = (board.level.getSize() / 2) + 1;
      if (player1Status.score() >= winningScore) {
        return Status.won_by_player;
      }

      if (player2Status.score() >= winningScore) {
        return Status.won_by_player;
      }

      if (board.squares.stream().allMatch(Square::isOccupied)) {
        return Status.draw;
      }

      return Status.in_progress;
    }

    boolean isCurrentPlayer(PlayerStatus playerStatus) {
      return isCurrentPlayer(playerStatus.player());
    }

    boolean isCurrentPlayer(Player player) {
      return currentPlayerStatus.isPresent() && currentPlayerStatus.get().player().id().equals(player.id());
    }

    Optional<PlayerStatus> getCurrentPlayer() {
      return currentPlayerStatus;
    }

    String getCurrentPlayerName() {
      return getCurrentPlayer()
          .map(PlayerStatus::player)
          .map(Player::name)
          .orElse("Unknown Player");
    }

    PlayerStatus getNextPlayer() {
      if (currentPlayerStatus.isEmpty()) {
        return player1Status;
      }

      if (currentPlayerStatus.get().player().id().equals(player1Status.player().id())) {
        return player2Status;
      }

      return player1Status;
    }

    boolean isPlayer1Turn() {
      return currentPlayerStatus.isEmpty() || currentPlayerStatus.get().player().id().equals(player1Status.player().id());
    }

    boolean isPlayer2Turn() {
      return !currentPlayerStatus.isEmpty() && currentPlayerStatus.get().player().id().equals(player2Status.player().id());
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
        String squareId) implements Command {}

    public record PlayerTurnCompleted(
        String gameId,
        String playerId) implements Command {}

    public record ForfeitMove(
        String gameId,
        String playerId,
        String message) implements Command {}

    public record GameCanceled(String gameId) implements Command {}

    public record CancelGame(String gameId, String reason) implements Command {}
  }

  // ============================================================
  // Events
  // ============================================================
  public sealed interface Event {

    @TypeName("game-created")
    public record GameCreated(
        String gameId,
        Status status,
        Instant createdAt,
        Instant updatedAt,
        Instant turnCompletedAt,
        Optional<Instant> finishedAt,
        PlayerStatus player1Status,
        PlayerStatus player2Status,
        Optional<PlayerStatus> currentPlayerStatus,
        List<Move> moveHistory,
        Board.Level level,
        Board board) implements Event {}

    @TypeName("move-made")
    public record MoveMade(
        String gameId,
        Status status,
        Instant updatedAt,
        PlayerStatus player1Status,
        PlayerStatus player2Status,
        Optional<PlayerStatus> currentPlayerStatus,
        List<Move> moveHistory,
        Board board) implements Event {}

    @TypeName("player-turn-completed")
    public record PlayerTurnCompleted(
        String gameId,
        Status status,
        Instant turnCompletedAt,
        PlayerStatus player1Status,
        PlayerStatus player2Status,
        Optional<PlayerStatus> currentPlayerStatus,
        List<Move> moveHistory) implements Event {}

    @TypeName("game-canceled")
    public record GameCanceled(
        String gameId,
        Status status,
        Instant updatedAt,
        Optional<Instant> finishedAt,
        PlayerStatus player1Status,
        PlayerStatus player2Status,
        Optional<PlayerStatus> currentPlayerStatus,
        String reason) implements Event {}

    @TypeName("move-forfeited")
    public record MoveForfeited(
        String gameId,
        Status status,
        Instant updatedAt,
        Optional<PlayerStatus> currentPlayerStatus,
        String message) implements Event {}

    @TypeName("game-finished")
    public record GameFinished(
        String gameId,
        Status status,
        Instant updatedAt,
        Optional<Instant> finishedAt) implements Event {}

    @TypeName("game-results")
    public record GameResults(
        String gameId,
        Status status,
        Instant updatedAt,
        PlayerStatus player1Status,
        PlayerStatus player2Status) implements Event {}
  }

  // ============================================================
  // Player
  // ============================================================
  public enum PlayerType {
    human,
    agent
  }

  public record Player(String id, PlayerType type, String name, String model) {
    static Player empty() {
      return new Player("", PlayerType.human, "", "");
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
  public record PlayerStatus(Player player, int moves, int score, boolean isWinner, ScoringMoves scoringMoves) {
    public PlayerStatus(Player player, int moves, int score, boolean isWinner) {
      this(player, moves, score, isWinner, ScoringMoves.create(player));
    }

    static PlayerStatus empty() {
      return new PlayerStatus(Player.empty(), 0, 0, false, ScoringMoves.empty());
    }

    PlayerStatus incrementMoves() {
      return new PlayerStatus(player, moves + 1, score, isWinner, scoringMoves);
    }

    PlayerStatus incrementScore(int scoreIncrement) {
      return new PlayerStatus(player, moves, score + scoreIncrement, isWinner, scoringMoves);
    }

    PlayerStatus makeMove(String squareId, Board.Level level, List<Move> moveHistory) {
      var move = new Square(squareId, player);
      var newScoringMoves = scoringMoves.scoreMove(move, level, moveHistory);
      return new PlayerStatus(player, moves + 1, newScoringMoves.totalScore(), isWinner, newScoringMoves);
    }

    PlayerStatus setWinner() {
      return new PlayerStatus(player, moves, score, true, scoringMoves);
    }

    PlayerStatus setLoser() {
      return new PlayerStatus(player, moves, score, false, scoringMoves);
    }
  }

  // ============================================================
  // Square in the board
  // ============================================================
  public record Square(String squareId, Optional<String> playerId) {
    public Square(String squareId, Player player) {
      this(squareId, Optional.of(player.id()));
    }

    boolean isOccupied() {
      return playerId.isPresent();
    }

    boolean isEmpty() {
      return !isOccupied();
    }

    public Square(String id) {
      this(id, Optional.empty());
    }

    Square withPlayer(Player player) {
      return new Square(squareId, player);
    }

    public int row() {
      return squareId.charAt(0) - 'A' + 1;
    }

    public int col() {
      return Integer.parseInt(squareId.substring(1));
    }
  }

  // ============================================================
  // ScoringMove
  // ============================================================
  public enum ScoringMoveType {
    horizontal,
    vertical,
    diagonal,
    adjacent,
    topToBottom,
    leftToRight
  }

  public record ScoringMove(Square move, ScoringMoveType type, int score, List<String> scoringSquares) {}

  public record ScoringMoves(String playerId, List<ScoringMove> scoringMoves) {
    static ScoringMoves empty() {
      return new ScoringMoves(Player.empty().id(), List.of());
    }

    static ScoringMoves create(Player player) {
      return new ScoringMoves(player.id(), List.of());
    }

    public int totalScore() {
      return scoringMoves.stream()
          .map(s -> s.score())
          .reduce(0, Integer::sum);
    }

    ScoringMoves withScoringMoves(List<ScoringMove> scoringMoves) {
      return new ScoringMoves(playerId, Stream.concat(this.scoringMoves.stream(), scoringMoves.stream()).toList());
    }

    ScoringMoves scoreMove(Square move, Board.Level level, List<Move> moveHistory) {
      if (!playerId.equals((move.playerId().orElse(Player.empty().id())))) {
        return this;
      }

      return withScoringMoves(scoreMoveHorizontal(move, level, moveHistory))
          .withScoringMoves(scoreMoveVertical(move, level, moveHistory))
          .withScoringMoves(scoreMoveDiagonal(move, level, moveHistory))
          .withScoringMoves(scoreMoveAdjacent(move, level, moveHistory))
          .withScoringMoves(scoreMoveSideToSide(move, level, moveHistory));
    }

    // ============================================================
    // ScoreMoveHorizontal
    // ============================================================
    List<ScoringMove> scoreMoveHorizontal(Square move, Board.Level level, List<Move> moveHistory) {
      var row = move.row();
      var groups = new ArrayList<List<Move>>();
      var group = new ArrayList<Move>();

      Stream.concat(moveHistory.stream(), Stream.of(new Move(move.squareId(), playerId)))
          .toList()
          .stream()
          .filter(m -> m.playerId().equals(playerId))
          .filter(m -> m.row() == row)
          .sorted(Comparator.comparingInt(m -> m.col()))
          .toList()
          .forEach(m -> {
            if (group.isEmpty() || m.col() == group.get(group.size() - 1).col() + 1) {
              group.add(m);
            } else {
              groups.add(group.stream().toList());
              group.clear();
              group.add(m);
            }
          });
      groups.add(group);

      var scoringMoves = groups.stream()
          .map(g -> {
            var score = scoreLine(move, level, g);
            var scoringSquares = g.stream().map(m -> m.squareId()).toList();
            return new ScoringMove(move, ScoringMoveType.horizontal, score, scoringSquares);
          })
          .filter(scoringMove -> scoringMove.score > 0)
          .toList();

      return scoringMoves;
    }

    // ============================================================
    // ScoreMoveVertical
    // ============================================================
    List<ScoringMove> scoreMoveVertical(Square move, Board.Level level, List<Move> moveHistory) {
      var col = move.col();
      var groups = new ArrayList<List<Move>>();
      var group = new ArrayList<Move>();

      Stream.concat(moveHistory.stream(), Stream.of(new Move(move.squareId(), playerId)))
          .toList()
          .stream()
          .filter(m -> m.playerId().equals(playerId))
          .filter(m -> m.col() == col)
          .sorted(Comparator.comparingInt(m -> m.row()))
          .toList()
          .forEach(m -> {
            if (group.isEmpty() || m.row() == group.get(group.size() - 1).row() + 1) {
              group.add(m);
            } else {
              groups.add(group.stream().toList());
              group.clear();
              group.add(m);
            }
          });
      groups.add(group);

      var scoringMoves = groups.stream()
          .map(g -> {
            var score = scoreLine(move, level, g);
            var scoringSquares = g.stream().map(m -> m.squareId()).toList();
            return new ScoringMove(move, ScoringMoveType.vertical, score, scoringSquares);
          })
          .filter(scoringMove -> scoringMove.score > 0)
          .toList();

      return scoringMoves;
    }

    enum DiagonalDirection {
      downRight,
      downLeft
    }

    // ============================================================
    // ScoreMoveDiagonal
    // ============================================================
    List<ScoringMove> scoreMoveDiagonal(Square move, Board.Level level, List<Move> moveHistory) {
      var downRight = scoreMoveDiagonal(DiagonalDirection.downRight, move, level, moveHistory);
      var downLeft = scoreMoveDiagonal(DiagonalDirection.downLeft, move, level, moveHistory);
      return Stream.concat(downRight.stream(), downLeft.stream()).toList();
    }

    List<ScoringMove> scoreMoveDiagonal(DiagonalDirection direction, Square move, Board.Level level, List<Move> moveHistory) {
      var groups = new ArrayList<List<Move>>();
      var group = new ArrayList<Move>();

      Stream.concat(moveHistory.stream(), Stream.of(new Move(move.squareId(), playerId)))
          .toList()
          .stream()
          .filter(m -> m.playerId().equals(playerId))
          .filter(m -> isDiagonal(direction, move, m))
          .sorted(Comparator.comparingInt(m -> m.row()))
          .toList()
          .forEach(m -> {
            if (group.isEmpty() || isDiagonallyConsecutive(direction, group, m)) {
              group.add(m);
            } else {
              groups.add(group.stream().toList());
              group.clear();
              group.add(m);
            }
          });
      groups.add(group);

      var scoringMoves = groups.stream()
          .map(g -> {
            var score = scoreLine(move, level, g);
            var scoringSquares = g.stream().map(m -> m.squareId()).toList();
            return new ScoringMove(move, ScoringMoveType.diagonal, score, scoringSquares);
          })
          .filter(scoringMove -> scoringMove.score > 0)
          .toList();

      return scoringMoves;
    }

    static boolean isDiagonal(DiagonalDirection direction, Square move, Move otherMove) {
      boolean isOnDiagonal = Math.abs(move.row() - otherMove.row()) == Math.abs(move.col() - otherMove.col());
      if (!isOnDiagonal) {
        return false;
      }
      if (move.row() == otherMove.row() && move.col() == otherMove.col()) {
        return true;
      }
      boolean isOtherMoveAboveMove = otherMove.row() < move.row();
      boolean isOtherMoveRightOfMove = otherMove.col() > move.col();
      if (direction == DiagonalDirection.downRight) {
        return isOtherMoveAboveMove && !isOtherMoveRightOfMove || !isOtherMoveAboveMove && isOtherMoveRightOfMove;
      } else {
        return isOtherMoveAboveMove && isOtherMoveRightOfMove || !isOtherMoveAboveMove && !isOtherMoveRightOfMove;
      }
    }

    static boolean isDiagonallyConsecutive(DiagonalDirection direction, List<Move> group, Move nextMove) {
      var lastMove = group.get(group.size() - 1);
      return (direction == DiagonalDirection.downRight && nextMove.row() - lastMove.row() == 1 && nextMove.col() - lastMove.col() == 1)
          || (direction == DiagonalDirection.downLeft && nextMove.row() - lastMove.row() == 1 && lastMove.col() - nextMove.col() == 1);
    }

    // ============================================================
    // ScoreMoveAdjacent
    // ============================================================
    List<ScoringMove> scoreMoveAdjacent(Square move, Board.Level level, List<Move> moveHistory) {
      var groups = new ArrayList<List<Move>>();
      var group = new ArrayList<Move>();

      Stream.concat(moveHistory.stream(), Stream.of(new Move(move.squareId(), playerId)))
          .toList()
          .stream()
          .filter(m -> m.playerId().equals(playerId))
          .filter(m -> isAdjacent(move, m))
          .sorted(Comparator.comparing(Move::squareId))
          .toList()
          .forEach(m -> {
            group.add(m);
          });
      groups.add(group);

      var scoringMoves = groups.stream()
          .map(g -> {
            var score = scoreAdjacent(level, g);
            var scoringSquares = g.stream().map(m -> m.squareId()).toList();
            return new ScoringMove(move, ScoringMoveType.adjacent, score, scoringSquares);
          })
          .filter(scoringMove -> scoringMove.score > 0)
          .toList();

      return scoringMoves;
    }

    static boolean isAdjacent(Square move, Move otherMove) {
      return move.row() == otherMove.row() && move.col() == otherMove.col() // same square
          || move.row() == otherMove.row() && Math.abs(move.col() - otherMove.col()) == 1 // left or right
          || move.col() == otherMove.col() && Math.abs(move.row() - otherMove.row()) == 1 // up or down
          || Math.abs(move.row() - otherMove.row()) == 1 && Math.abs(move.col() - otherMove.col()) == 1; // diagonal
    }

    static int scoreAdjacent(Board.Level level, List<Move> moves) {
      var adjacentSquaresToScore = Math.min(8, level.concurrentSquaresToScore());
      return moves.size() - 1 >= adjacentSquaresToScore ? Math.max(2, moves.size() - adjacentSquaresToScore) : 0;
    }

    // ============================================================
    // ScoreMoveSideToSide
    // ============================================================
    List<ScoringMove> scoreMoveSideToSide(Square move, Board.Level level, List<Move> moveHistory) {
      var newMoveHistory = Stream.concat(moveHistory.stream(), Stream.of(new Move(move.squareId(), playerId))).toList();

      var movesInRow = findPlayerMovesInRow(1, newMoveHistory, playerId);
      var scoringMovesTopToBottom = movesInRow.stream()
          .map(m -> scoreMoveTopToBottom(m.row() + 1, level.getSize(), move, List.of(m), newMoveHistory))
          .flatMap(List::stream)
          .filter(scoringMove -> scoringMove.score > 0)
          .toList();

      var movesInCol = findPlayerMovesInCol(1, newMoveHistory, playerId);
      var scoringMovesLeftToRight = movesInCol.stream()
          .map(m -> scoreMoveLeftToRight(m.col() + 1, level.getSize(), move, List.of(m), newMoveHistory))
          .flatMap(List::stream)
          .filter(scoringMove -> scoringMove.score > 0)
          .toList();

      return Stream.concat(scoringMovesTopToBottom.stream(), scoringMovesLeftToRight.stream()).toList();
    }

    List<ScoringMove> scoreMoveTopToBottom(int row, int maxRow, Square move, List<Move> path, List<Move> moveHistory) {
      if (row == maxRow) {
        var nextMovesInPath = findPlayerMovesInRow(row, moveHistory, playerId)
            .stream()
            .filter(m -> isInTopToBottomPath(path.get(path.size() - 1), m))
            .toList();
        var finalPath = Stream.concat(path.stream(), nextMovesInPath.stream()).toList();
        var scoringSquares = finalPath.stream().map(m -> m.squareId()).toList();
        return scoringSquares.size() >= maxRow
            ? List.of(new ScoringMove(move, ScoringMoveType.topToBottom, maxRow, scoringSquares))
            : List.of();
      }

      return findPlayerMovesInRow(row, moveHistory, playerId)
          .stream()
          .filter(m -> isInTopToBottomPath(path.get(path.size() - 1), m))
          .map(m -> scoreMoveTopToBottom(m.row() + 1, maxRow, move, Stream.concat(path.stream(), Stream.of(m)).toList(), moveHistory))
          .flatMap(List::stream)
          .toList();
    }

    static boolean isInTopToBottomPath(Move move, Move otherMove) {
      if (move.playerId().equals(otherMove.playerId())) {
        return Math.abs(move.col() - otherMove.col()) <= 1 && move.row() - otherMove.row() == -1;
      }
      return false;
    }

    List<ScoringMove> scoreMoveLeftToRight(int col, int maxCol, Square move, List<Move> path, List<Move> moveHistory) {
      if (col == maxCol) {
        var nextMovesInPath = findPlayerMovesInCol(col, moveHistory, playerId)
            .stream()
            .filter(m -> isInLeftToRightPath(path.get(path.size() - 1), m))
            .toList();
        var finalPath = Stream.concat(path.stream(), nextMovesInPath.stream()).toList();
        var scoringSquares = finalPath.stream().map(m -> m.squareId()).toList();
        return scoringSquares.size() >= maxCol
            ? List.of(new ScoringMove(move, ScoringMoveType.leftToRight, maxCol, scoringSquares))
            : List.of();
      }

      return findPlayerMovesInCol(col, moveHistory, playerId)
          .stream()
          .filter(m -> isInLeftToRightPath(path.get(path.size() - 1), m))
          .map(m -> scoreMoveLeftToRight(m.col() + 1, maxCol, move, Stream.concat(path.stream(), Stream.of(m)).toList(), moveHistory))
          .flatMap(List::stream)
          .toList();
    }

    static boolean isInLeftToRightPath(Move move, Move otherMove) {
      if (move.playerId().equals(otherMove.playerId())) {
        return Math.abs(move.row() - otherMove.row()) <= 1 && move.col() - otherMove.col() == -1;
      }
      return false;
    }

    static List<Move> findPlayerMovesInRow(int row, List<Move> moveHistory, String playerId) {
      return moveHistory.stream()
          .filter(m -> m.playerId().equals(playerId))
          .filter(m -> m.row() == row)
          .toList();
    }

    static List<Move> findPlayerMovesInCol(int col, List<Move> moveHistory, String playerId) {
      return moveHistory.stream()
          .filter(m -> m.playerId().equals(playerId))
          .filter(m -> m.col() == col)
          .toList();
    }

    // ============================================================
    // ScoreLine
    // ============================================================
    static int scoreLine(Square move, Board.Level level, List<Move> moves) {
      // check if line is too short
      if (moves.size() < level.concurrentSquaresToScore()) {
        return 0;
      }

      // check if move is contained in the line
      if (moves.stream().filter(m -> m.squareId().equals(move.squareId())).count() != 1) {
        return 0;
      }

      // check if move is at start or end of the line
      boolean isMoveAtStartOrEnd = moves.get(0).squareId().equals(move.squareId()) || moves.get(moves.size() - 1).squareId().equals(move.squareId());
      if (isMoveAtStartOrEnd) {
        return 1;
      }

      // check if line is longer than the concurrent squares to score
      // this is a high scoring move when connecting 2 lines into a single line in the same direction
      if (moves.size() > level.concurrentSquaresToScore()) {
        return moves.size() - level.concurrentSquaresToScore() + 1;
      }

      // line is exactly the concurrent squares to score
      return 1;
    }
  }

  // ============================================================
  // Board in the game
  // ============================================================
  public record Board(Level level, List<Square> squares) {
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

      public int lineSquaresToScore() {
        return getSize() / 2 + 1;
      }

      public int concurrentSquaresToScore() {
        return Math.min(8, lineSquaresToScore());
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
                return new Square(rowChar + String.valueOf(colNum));
              }))
          .toList());
    }

    public Optional<Square> squareAt(String id) {
      return squares.stream()
          .filter(square -> square.squareId().equals(id))
          .findFirst();
    }

    Board withSquare(String id, Player player) {
      return new Board(level, squares.stream()
          .map(square -> square.squareId().equals(id) ? square.withPlayer(player) : square)
          .toList());
    }
  }

  public record Move(String squareId, String playerId, long thinkMs) {
    public Move(String squareId, String playerId) {
      this(squareId, playerId, 0);
    }

    int row() {
      return squareId.charAt(0) - 'A' + 1;
    }

    int col() {
      return Integer.parseInt(squareId.substring(1));
    }
  }
}
