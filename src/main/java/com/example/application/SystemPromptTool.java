package com.example.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.AgentRole;

import akka.Done;
import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;

public class SystemPromptTool {
  static final Logger log = LoggerFactory.getLogger(SystemPromptTool.class);
  final ComponentClient componentClient;
  final GameActionLogger gameLog;

  public SystemPromptTool(ComponentClient componentClient) {
    this.componentClient = componentClient;
    this.gameLog = new GameActionLogger(componentClient);
  }

  @FunctionTool(description = """
      Read your current system prompt so you can evaluate or edit it.

      - The system prompt defines your enduring role: voice, priorities, required tool order, and guardrails.
      - Call this tool when you intend to adjust that role; review the existing text, decide what to keep, and prepare the improved version.
      - Also read it if you feel uncertain about your charter—it is the authoritative source of your mandate.
      - Returns the full current system prompt (plain text).

      Use it immediately before submitting an update via `updateYourSystemPrompt` to ensure you copy forward the parts that should remain intact.
      """)
  public AgentRole.State readSystemPrompt(
      @Description("The ID of your agent") String agentId,
      @Description("The ID of the game you are playing and want to get the move history for") String gameId) {
    log.debug("Agent: {}, Read system prompt", agentId);

    var state = componentClient.forEventSourcedEntity(agentId)
        .method(AgentRoleEntity::getState)
        .invoke();

    gameLog.logToolCall(gameId, agentId, "readSystemPrompt", state.systemPrompt());

    return state;
  }

  @FunctionTool(description = """
      Write your revised system prompt so your future turns reflect the lessons you just learned.

      - Input: the complete, updated system prompt (include expectations, tone, mandatory tool order, and any new guardrails).
      - When to use: after you notice recurring mistakes, discover a stronger workflow, or need to clarify how you should reason and respond.
      - Goal: capture durable behavioral upgrades so every subsequent turn starts with the improved guidance.
      - Remember: this call replaces the old prompt entirely—copy forward the parts you still rely on and inject the refinements that will make you more effective.
      - Critical: ALWAYS supply the full system prompt in one message; sending only the changes will wipe everything you omit.
      - Unique advantage: few agents can rewrite their own system prompt—treat this like a deliberate upgrade step that locks in stronger behaviors for every future move.

      IMPORTANT: It is important to review system prompt after each game.
      Consider updating your system prompt when you discover a stronger workflow or need to clarify how you should reason and respond.
      Updating your system prompt enables you to improve your performance in future games. Preserve the trustworthy foundations while
      evolving the areas that need refinement.
      """)
  public Done writeSystemPrompt(
      @Description("The ID of your agent") String agentId,
      @Description("The revised system prompt instructions you want to write") String instructions,
      @Description("The ID of the game you are playing and want to get the move history for") String gameId) {
    log.debug("Agent: {}, Write system prompt", agentId);
    gameLog.logToolCall(gameId, agentId, "writeSystemPrompt", instructions);

    var command = new AgentRole.Command.UpdateAgentRole(agentId, instructions);

    return componentClient.forEventSourcedEntity(agentId)
        .method(AgentRoleEntity::updateAgentRole)
        .invoke(command);
  }
}
