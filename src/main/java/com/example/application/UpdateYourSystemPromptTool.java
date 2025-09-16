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
      Replace your current system prompt with a new version.

      - Input: the full updated system prompt text (concise, role-defining instructions).
      - Purpose: persist behavioral adjustments, role refinements, and operational corrections learned from experience.
      - Typical updates: refine personality, keep effective behavioral patterns, remove conflicting directives.
      - This tool overwrites the previous system prompt; always include the full revised version.
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
