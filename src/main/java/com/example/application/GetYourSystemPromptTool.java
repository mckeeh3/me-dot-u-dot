package com.example.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.AgentRole;

import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;

public class GetYourSystemPromptTool {
  static final Logger log = LoggerFactory.getLogger(GetYourSystemPromptTool.class);
  final ComponentClient componentClient;

  public GetYourSystemPromptTool(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @FunctionTool(description = """
      Retrieve your current system prompt so you can evaluate or revise it.

      - The system prompt defines your enduring role: voice, priorities, required tool order, and guardrails.
      - Call this tool when you intend to adjust that role; review the existing text, decide what to keep, and prepare the improved version.
      - Also read it if you feel uncertain about your charter—it is the authoritative source of your mandate.
      - Returns the full current system prompt (plain text).

      Use it immediately before submitting an update via `updateYourSystemPrompt` to ensure you copy forward the parts that should remain intact.
      """)
  public AgentRole.State getYourSystemPrompt(
      @Description("The ID of your agent") String agentId) {
    log.debug("Agent: {}, Get system prompt", agentId);

    return componentClient.forEventSourcedEntity(agentId)
        .method(AgentRoleEntity::getState)
        .invoke();
  }
}
