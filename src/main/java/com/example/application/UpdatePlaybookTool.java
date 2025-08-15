package com.example.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.Playbook;

import akka.Done;
import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;

public class UpdatePlaybookTool {
  static final Logger log = LoggerFactory.getLogger(UpdatePlaybookTool.class);
  final ComponentClient componentClient;

  public UpdatePlaybookTool(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @FunctionTool(description = """
      Replaces your (the agent’s) current playbook—a persistent, free-form text instructions keyed by agentId—
      with a new version of its instructions. The playbook is the your self-authored knowledge base: it
      captures discoveries, decision principles, tactics, and corrections distilled from experience.
      Updating it is how you (the agent/model) learn, turning raw outcomes into reusable guidance and gradually
      maturing into an expert player. Typical updates refine language for clarity, promote effective
      ideas, and remove stale or conflicting guidance so that future decisions are grounded in the best
      available understanding.
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
