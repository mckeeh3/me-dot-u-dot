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
      Retrieve your current playbook text so you can apply or revise it.

      - The playbook is your tactical memory: a persistent, self-authored document containing proven openings, counter-moves, and heuristics.
      - Use this tool before planning a move to anchor your reasoning in what already works.
      - Also call it immediately before updating the playbook; copy forward the guidance you still trust and edit only what needs to change.
      - Returns the full current playbook (plain text).
      """)
  public Playbook.State getYourPlaybook(
      @Description("The ID of your agent") String agentId) {
    log.debug("Player: {}, Get playbook", agentId);

    return componentClient.forEventSourcedEntity(agentId)
        .method(PlaybookEntity::getState)
        .invoke();
  }
}
