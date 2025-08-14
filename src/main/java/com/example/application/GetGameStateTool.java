package com.example.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.DotGame;

import akka.javasdk.client.ComponentClient;
import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;

public class GetGameStateTool {
  static final Logger log = LoggerFactory.getLogger(GetGameStateTool.class);
  final ComponentClient componentClient;

  public GetGameStateTool(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @FunctionTool(description = """
      Retrieves the authoritative, current state of a Dot Game from the backend.

      The returned value is a DotGame.State with these fields:
      - gameId: unique id of the game
      - createdAt: timestamp when the game was created (ISO-8601)
      - board:
        - level: one..nine (controls size). Sizes: one=5, two=7, three=9, four=11, five=13, six=15, seven=17, eight=19, nine=21
        - dots: list of cells; each has:
          - id: coordinate string with A1 at top-left
          - player: null if empty, otherwise { id, type (human|agent), name }
      - status: empty | in_progress | won_by_player | draw
      - player1Status: { player: { id, type, name }, moves, score, isWinner }
      - player2Status: { player: { id, type, name }, moves, score, isWinner }
      - currentPlayer: optional PlayerStatus present only during in_progress indicating whose turn it is
      - moveHistory: ordered list of { dotId, playerId } for all moves made

      Notes for tool use:
      - Call this when you need the latest board, scores, or whose turn it is.
      - Use board.level to derive board size; valid coordinates run from A1 (top-left).
      - Use currentPlayer to check if you are the current player; do not infer from moveHistory.
      - This is the source of truth; prefer this over cached assumptions.
      """)
  public DotGame.State getGameState(
      @Description("The ID of the game you are playing") String gameId) {
    log.debug("Get game state: {}", gameId);

    return componentClient.forEventSourcedEntity(gameId)
        .method(DotGameEntity::getState)
        .invoke();
  }
}
