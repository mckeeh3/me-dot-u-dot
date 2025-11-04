package com.example.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.Playbook;

import akka.Done;
import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;

public class PlaybookTool {
  static final Logger log = LoggerFactory.getLogger(PlaybookTool.class);
  final ComponentClient componentClient;
  final GameActionLogger gameLog;

  public PlaybookTool(ComponentClient componentClient) {
    this.componentClient = componentClient;
    this.gameLog = new GameActionLogger(componentClient);
  }

  @FunctionTool(description = """
      Read your current playbook contents.

      - The playbook is your tactical memory: a persistent, self-authored document containing proven openings, counter-moves, and heuristics.
      - Returns the full current playbook contents.
      """)
  public Playbook.State readPlaybook(
      @Description("The ID of your agent") String agentId,
      @Description("The ID of the game you are playing and want to get the playbook for") String gameId) {
    log.debug("AgentId: {}, GameId: {}, Read playbook", agentId, gameId);

    var state = componentClient.forEventSourcedEntity(agentId)
        .method(PlaybookEntity::getState)
        .invoke();

    gameLog.logToolCall(gameId, agentId, "readPlaybook", state.instructions().isEmpty() ? "Playbook is empty" : state.instructions());

    return state;
  }

  @FunctionTool(description = """
      Write your revised playbook contents.

      - Input: the complete revised playbook contents.
      - Critical: This tool completely replaces the playbook, so you must provide the full revised playbook contents in one message.
      """)
  public Done writePlaybook(
      @Description("The ID of your agent") String agentId,
      @Description("The ID of the game you are playing and want to get the playbook for") String gameId,
      @Description("The revised playbook contents you want to write") String revisedPlaybookContents) {
    log.debug("AgentId: {}, GameId: {}, Write playbook", agentId, gameId);
    gameLog.logToolCall(gameId, agentId, "writePlaybook", revisedPlaybookContents);

    var command = new Playbook.Command.WritePlaybook(agentId, revisedPlaybookContents);

    return componentClient.forEventSourcedEntity(agentId)
        .method(PlaybookEntity::writePlaybook)
        .invoke(command);
  }
}
