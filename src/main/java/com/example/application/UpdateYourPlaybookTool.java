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
      Replace your current playbook with a new version.

      - Input: the full updated playbook text (concise, self-authored instructions).
      - Purpose: persist lessons, tactics, and corrections learned from experience.
      - Typical updates: refine clarity, keep effective ideas, remove stale or conflicting guidance.
      - This tool overwrites the previous playbook; always include the full revised version.
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
