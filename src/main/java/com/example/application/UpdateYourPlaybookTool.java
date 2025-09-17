package com.example.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.Playbook;

import akka.Done;
import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;

public class UpdateYourPlaybookTool {
  static final Logger log = LoggerFactory.getLogger(UpdateYourPlaybookTool.class);
  final ComponentClient componentClient;

  public UpdateYourPlaybookTool(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @FunctionTool(description = """
      Rewrite your playbook to capture the tactical knowledge you want to reuse next time you play.

      - Input: the complete, polished playbook (succinct tactics, heuristics, counter-strategies, and reminders).
      - When to use: after a turn or game when you have a concrete lesson, new pattern, or clarified move sequence worth memorialising.
      - Goal: turn raw observations into actionable guidance that future turns can follow without re-deriving the insight.
      - Remember: the playbook differs from the system prompt—the playbook focuses on situational tactics, while the system prompt governs your overall role. This call replaces the playbook entirely, so bring forward the advice that still works and revise the parts that should change.
      """)
  public Done updatePlaybook(
      @Description("The ID of your agent") String agentId,
      @Description("The instructions you want to update your playbook with") String instructions) {
    log.debug("Player: {}, Update playbook", agentId);

    var command = new Playbook.Command.UpdatePlaybook(agentId, instructions);

    return componentClient.forEventSourcedEntity(agentId)
        .method(PlaybookEntity::updatePlaybook)
        .invoke(command);
  }
}
