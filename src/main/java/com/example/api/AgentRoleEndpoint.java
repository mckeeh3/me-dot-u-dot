package com.example.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.application.AgentRoleEntity;
import com.example.application.AgentRoleJournalView;
import com.example.domain.AgentRole;

import akka.Done;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/agent-role")
public class AgentRoleEndpoint {
  static final Logger log = LoggerFactory.getLogger(AgentRoleEndpoint.class);
  final ComponentClient componentClient;

  public AgentRoleEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/get-journal-by-agent-id-down")
  public AgentRoleJournalView.Journals getJournalByAgentIdDown(AgentRoleJournalView.GetByAgentIdDownRequest request) {
    log.info("Get journal down by agent id: {}", request);

    var queryRequest = new AgentRoleJournalView.GetByAgentIdDownRequest(request.agentId(), request.sequenceId());

    return componentClient
        .forView()
        .method(AgentRoleJournalView::getByAgentIdDown)
        .invoke(queryRequest);
  }

  @Post("/get-journal-by-agent-id-up")
  public AgentRoleJournalView.Journals getJournalByAgentIdUp(AgentRoleJournalView.GetByAgentIdUpRequest request) {
    log.info("Get journal up by agent id: {}", request);

    var queryRequest = new AgentRoleJournalView.GetByAgentIdUpRequest(request.agentId(), request.sequenceId());

    return componentClient
        .forView()
        .method(AgentRoleJournalView::getByAgentIdUp)
        .invoke(queryRequest);
  }

  @Post("/get-journal-by-agent-id-and-sequence")
  public AgentRoleJournalView.Journals getJournalByAgentIdAndSequence(AgentRoleJournalView.GetByAgentIdAndSequenceRequest request) {
    log.info("Get journal by agent id and sequence: {}", request);

    return componentClient.forView()
        .method(AgentRoleJournalView::getByAgentIdAndSequence)
        .invoke(request);
  }

  // this is intended to be used when the initial system prompt is changed, allowing for updating an agent player's system
  // prompt
  @Post("/reset-agent-role")
  public Done resetAgentRole(AgentRole.Command.ResetAgentRole request) {
    log.info("Reset agent role: {}", request);

    return componentClient.forEventSourcedEntity(request.agentId())
        .method(AgentRoleEntity::resetAgentRole)
        .invoke(request);
  }
}
