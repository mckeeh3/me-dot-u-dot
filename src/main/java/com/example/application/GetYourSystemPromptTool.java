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
      Retrieve your current system prompt text when you intend to update your system prompt.

      - The system prompt is a persistent instruction set that defines your agent role and behavior.
      - It contains your core directives, personality, and operational guidelines.
      - Use it to understand your current configuration and maintain consistent behavior across interactions.
      - Returns the full current system prompt (as plain text).

      Use this tool ONLY when you intend to update your system prompt.
      """)
  public AgentRole.State getYourSystemPrompt(
      @Description("The ID of your agent") String agentId) {
    log.debug("Agent: {}, Get system prompt", agentId);

    return componentClient.forEventSourcedEntity(agentId)
        .method(AgentRoleEntity::getState)
        .invoke();
  }
}
