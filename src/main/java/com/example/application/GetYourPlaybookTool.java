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
      Retrieves your (the agent’s) playbook—a persistent, free-form text block keyed by you.
      The playbook is the your living instruction set: it captures discoveries, hypotheses,
      tactics, decision principles, and corrections distilled from experience. By reading it,
      you (the agent/model) ground your next decisions in accumulated knowledge, maintains
      consistency across turns and games, and progressively improves from trial, error, and
      observed outcomes. Over time, a well-maintained playbook becomes your portable expertise:
      concise, actionable guidance that generalizes to new positions and situations without
      relying on any hidden assumptions.
      """)
  public Playbook.State getYourPlaybook(
      @Description("The ID of your agent") String agentId) {
    log.debug("Get playbook for agent: {}", agentId);

    return componentClient.forEventSourcedEntity(agentId)
        .method(PlaybookEntity::getState)
        .invoke();
  }
}
