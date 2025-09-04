package com.example.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.DotGame;

import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;

public class MakeMoveTool {
  static final Logger log = LoggerFactory.getLogger(MakeMoveTool.class);
  final ComponentClient componentClient;

  public MakeMoveTool(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @FunctionTool(description = """
      Submit a move for the specified game.

      - Use ONLY when it is your turn and the game is in progress.
      - Input: a single coordinate string (e.g., "C3").
      - Do not include natural language or multiple coordinates.
      - This tool does NOT explain rules or validate strategy — it only records the move.

      The tool will return "Move completed" if the move was successful, otherwise it will return "Move rejected".
      """)
  public String makeMove(
      @Description("The ID of the game you are making a move in") String gameId,
      @Description("The ID of your player/agent id for this game") String agentId,
      @Description("""
          The board coordinate to claim (e.g., "C3"). Coordinates start
          at A1 in the top-left and extend to the board size determined by level
          """) String dotId) {
    log.debug("AgentId: {}, Make move: {} in game: {}", agentId, dotId, gameId);

    var command = new DotGame.Command.MakeMove(gameId, agentId, dotId);

    var stateBeforeMove = componentClient.forEventSourcedEntity(gameId)
        .method(DotGameEntity::getState)
        .invoke();

    var stateAfterMove = componentClient.forEventSourcedEntity(gameId)
        .method(DotGameEntity::makeMove)
        .invoke(command);

    var gameOver = stateAfterMove.status() != DotGame.Status.in_progress;
    var moveCompleted = stateBeforeMove.moveHistory().size() < stateAfterMove.moveHistory().size();
    var areYouCurrentPlayer = stateAfterMove.currentPlayer().isPresent() && stateAfterMove.currentPlayer().get().player().id().equals(agentId);

    if (moveCompleted && gameOver) {
      var result = "Move completed, game over, you %s".formatted(stateAfterMove.status() == DotGame.Status.won_by_player ? "won" : "lost");
      log.debug(result);

      return result;
    }

    if (moveCompleted) {
      var result = "Move completed, it's your opponent's turn";
      log.debug(result);

      return result;
    }

    var moveResult = "Move %s, you %s the current player, %s"
        .formatted(
            (moveCompleted ? "completed" : "rejected"),
            (areYouCurrentPlayer ? "are" : "are not"),
            (areYouCurrentPlayer ? "it's still your turn, try again" : "it's your opponent's turn"));

    log.debug(moveResult);

    return moveResult;
  }
}
