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
      Submit a move to the backend for the specified game.

      This tool only performs the action of making a move. It does not provide
      instructions or rules for how to play. As you (the agent) gain experience and
      learn from prior game states and outcomes, that knowledge can guide
      your future move choices. Use this tool when you have decided which dot
      to claim next.
      """)
  public DotGame.State makeMove(
      @Description("The ID of the game you are making a move in") String gameId,
      @Description("The ID of your player/agent id for this game") String agentId,
      @Description("""
          The board coordinate to claim (e.g., "C3"). Coordinates start
          at A1 in the top-left and extend to the board size determined by level
          """) String dotId) {
    log.debug("Player: {}, Make move: {} in game: {}", agentId, dotId, gameId);

    var command = new DotGame.Command.MakeMove(gameId, agentId, dotId);

    return componentClient.forEventSourcedEntity(gameId)
        .method(DotGameEntity::makeMove)
        .invoke(command);
  }
}
