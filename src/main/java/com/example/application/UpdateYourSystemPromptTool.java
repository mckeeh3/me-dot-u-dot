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
