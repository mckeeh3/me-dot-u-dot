package com.example.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.AgentRole;

import akka.Done;
import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;

public class UpdateYourSystemPromptTool {
  static final Logger log = LoggerFactory.getLogger(UpdateYourSystemPromptTool.class);
  final ComponentClient componentClient;

  public UpdateYourSystemPromptTool(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @FunctionTool(description = """
      Rewrite your system prompt so your future turns reflect the lessons you just learned.

      - Input: the complete, updated system prompt (include expectations, tone, mandatory tool order, and any new guardrails).
      - When to use: after you notice recurring mistakes, discover a stronger workflow, or need to clarify how you should reason and respond.
      - Goal: capture durable behavioral upgrades so every subsequent turn starts with the improved guidance.
      - Remember: this call replaces the old prompt entirely—copy forward the parts you still rely on and inject the refinements that will make you more effective.
      - Critical: ALWAYS supply the full system prompt in one message; sending only the changes will wipe everything you omit.
      - Unique advantage: few agents can rewrite their own system prompt—treat this like a deliberate upgrade step that locks in stronger behaviors for every future move.

      IMPORTANT: It is important to review system prompt after each game.
      Consider updating your system prompt when you discover a stronger workflow or need to clarify how you should reason and respond.
      Updating your system prompt enables you to improve your performance in future games. Preserve the trustworthy foundations while evolving the areas that need refinement.
      """)
  public Done updateSystemPrompt(
      @Description("The ID of your agent") String agentId,
      @Description("The system prompt instructions you want to update your agent role with") String instructions) {
    log.debug("Agent: {}, Update system prompt", agentId);

    var command = new AgentRole.Command.UpdateAgentRole(agentId, instructions);

    return componentClient.forEventSourcedEntity(agentId)
        .method(AgentRoleEntity::updateAgentRole)
        .invoke(command);
  }
}
