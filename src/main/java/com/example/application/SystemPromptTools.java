package com.example.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.AgentRole;

import akka.Done;
import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;

public class SystemPromptTools {
  static final Logger log = LoggerFactory.getLogger(SystemPromptTools.class);
  final ComponentClient componentClient;
  final GameActionLogger gameLog;

  public SystemPromptTools(ComponentClient componentClient) {
    this.componentClient = componentClient;
    this.gameLog = new GameActionLogger(componentClient);
  }

  @FunctionTool(description = """
      Read your current system prompt contents.

      - The system prompt defines your enduring role: voice, priorities, required tool order, and guardrails.
      - Returns the full current system prompt contents.
      """)
  public AgentRole.State readSystemPrompt(
      @Description("The ID of your agent") String agentId,
      @Description("The ID of the game you are playing and want to get the move history for") String gameId) {
    log.debug("AgentId: {}, GameId: {}, Read system prompt", agentId, gameId);

    var state = componentClient.forEventSourcedEntity(agentId)
        .method(AgentRoleEntity::getState)
        .invoke();

    gameLog.logToolCall(gameId, agentId, "readSystemPrompt", state.systemPrompt());

    return state;
  }

  @FunctionTool(description = """
      Write your revised system prompt contents.

      - Input: the complete, revised system prompt contents.
      - Critical: This tool completely replaces the system prompt, so you must provide the full revised system prompt contents in one message.
      """)
  public Done writeSystemPrompt(
      @Description("The ID of your agent") String agentId,
      @Description("The ID of the game you are playing and want to get the move history for") String gameId,
      @Description("The revised system prompt contents you want to write") String revisedSystemPromptContents) {
    log.debug("AgentId: {}, GameId: {}, Write system prompt", agentId, gameId);

    var currentState = componentClient.forEventSourcedEntity(agentId)
        .method(AgentRoleEntity::getState)
        .invoke();

    var currentPrompt = currentState.systemPrompt();
    var finalPrompt = revisedSystemPromptContents;

    if (currentPrompt != null && !currentPrompt.isBlank() && revisedSystemPromptContents != null) {
      var currentLength = currentPrompt.length();
      var revisedLength = revisedSystemPromptContents.length();

      // If the revised system prompt is less than 33% of the current system prompt, append it to the current system prompt.
      // This is to avoid overwriting the current system prompt with a too short revised system prompt.
      // There have been cases where an agent mistakenly thinks it is appending to the system prompt instead of replacing it.
      if (currentLength > 0 && revisedLength > 0 && revisedLength < (currentLength * 0.33)) {
        log.warn(
            "AgentId: {}, GameId: {}, Revised system prompt shorter than 33% of existing prompt. Appending instead of replacing.",
            agentId,
            gameId);
        finalPrompt = currentPrompt + "\n\n" + revisedSystemPromptContents;
        var message = "System prompt revision too short (%d vs %d chars). Appended instead of replacing.".formatted(revisedLength, currentLength);
        gameLog.logGuardrailEvent(gameId, agentId, message);
      }
    }

    gameLog.logToolCall(gameId, agentId, "writeSystemPrompt", finalPrompt);

    var command = new AgentRole.Command.WriteAgentRole(agentId, finalPrompt);

    return componentClient.forEventSourcedEntity(agentId)
        .method(AgentRoleEntity::writeAgentRole)
        .invoke(command);
  }
}
