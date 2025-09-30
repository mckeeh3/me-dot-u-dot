package com.example.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.Playbook;

import akka.Done;
import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;

public class PlaybookTool {
  static final Logger log = LoggerFactory.getLogger(PlaybookTool.class);
  final ComponentClient componentClient;

  public PlaybookTool(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @FunctionTool(description = """
      Read your current playbook text so you can apply or edit it.

      - The playbook is your tactical memory: a persistent, self-authored document containing proven openings, counter-moves, and heuristics.
      - Use this tool before planning a move to anchor your reasoning in what already works.
      - Also call it immediately before updating the playbook; copy forward the guidance you still trust and edit only what needs to change.
      - Returns the full current playbook (plain text).
      """)
  public Playbook.State readYourPlaybook(
      @Description("The ID of your agent") String agentId) {
    log.debug("Player: {}, Get playbook", agentId);

    return componentClient.forEventSourcedEntity(agentId)
        .method(PlaybookEntity::getState)
        .invoke();
  }

  @FunctionTool(description = """
      Write your revised playbook to capture the tactical knowledge you want to reuse next time you play.

      - Input: the complete, polished playbook (succinct tactics, heuristics, counter-strategies, and reminders).
      - When to use: after a turn or game when you have a concrete lesson, new pattern, or clarified move sequence worth memorializing.
      - Goal: turn raw observations into actionable guidance that future turns can follow without re-deriving the insight.
      - Remember: the playbook differs from the system prompt—the playbook focuses on situational tactics, while the system prompt
      governs your overall role. This call replaces the playbook entirely, so bring forward the advice that still works and revise
      the parts that should change.
      - Critical: ALWAYS provide the fully updated playbook text on every call—partial snippets or diffs will overwrite the prior content
      and erase everything you omit.

      IMPORTANT: It is important to review and update your playbook after each game to capture your learnings and experience from the game
      to improve your performance in future games.
      Also, after interesting moves, such as scoring moves, you should consider updating your playbook to capture the move and the reason
      why it was successful.
      If you don't update your playbook, you will not be able to improve your performance in future games. When you do update it, carry
      forward the proven guidance and only rewrite the specific sections that should evolve.
      """)
  public Done writeYourPlaybook(
      @Description("The ID of your agent") String agentId,
      @Description("The revised playbook instructions you want to write") String instructions) {
    log.debug("Player: {}, Update playbook", agentId);

    var command = new Playbook.Command.UpdatePlaybook(agentId, instructions);

    return componentClient.forEventSourcedEntity(agentId)
        .method(PlaybookEntity::updatePlaybook)
        .invoke(command);
  }
}
