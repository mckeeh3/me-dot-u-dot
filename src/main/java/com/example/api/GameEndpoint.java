package com.example.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import com.example.application.GameAgent;

/**
 * Game endpoint for the me-dot-u-dot game. Handles player moves and AI responses.
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint
public class GameEndpoint {

  public record GameMove(String gameId, int row, int col, String gameState) {}

  public record GameResponse(String move, String gameState, String message) {}

  private final ComponentClient componentClient;

  public GameEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/api/game/move")
  public GameResponse makeMove(GameMove request) {
    // Update game state with player's move
    var updatedGameState = updateGameState(request.gameState, request.row, request.col, 1);

    // Get AI's move
    var aiMove = componentClient
        .forAgent()
        .inSession(request.gameId)
        .method(GameAgent::selectMove)
        .invoke(updatedGameState);

    // Parse AI move and update game state
    var aiCoords = aiMove.split(",");
    var aiRow = Integer.parseInt(aiCoords[0]);
    var aiCol = Integer.parseInt(aiCoords[1]);
    var finalGameState = updateGameState(updatedGameState, aiRow, aiCol, 2);

    return new GameResponse(
        aiMove,
        finalGameState,
        "AI moved to " + aiMove);
  }

  private String updateGameState(String gameState, int row, int col, int player) {
    var rows = gameState.split("\\|");
    var cells = rows[row].split(",");
    cells[col] = String.valueOf(player);
    rows[row] = String.join(",", cells);

    return String.join("|", rows);
  }
}
