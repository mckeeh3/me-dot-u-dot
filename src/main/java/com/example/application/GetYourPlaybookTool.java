package com.example.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.Playbook;

import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;

public class GetYourPlaybookTool {
  static final Logger log = LoggerFactory.getLogger(GetYourPlaybookTool.class);
  final ComponentClient componentClient;

  public GetYourPlaybookTool(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @FunctionTool(description = """
      Retrieve your current playbook text.

      - The playbook is a persistent, free-form instruction set authored by you.
      - It contains discoveries, tactics, and lessons distilled from prior games.
      - Use it to ground your next move decisions and maintain consistency across turns.
      - Returns the full current playbook (as plain text).
      """)
  public Playbook.State getYourPlaybook(
      @Description("The ID of your agent") String agentId) {
    log.debug("Player: {}, Get playbook", agentId);

    return componentClient.forEventSourcedEntity(agentId)
        .method(PlaybookEntity::getState)
        .invoke();
  }
}
