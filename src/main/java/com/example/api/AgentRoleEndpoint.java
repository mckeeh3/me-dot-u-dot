package com.example.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.application.AgentRoleJournalView;

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
}
