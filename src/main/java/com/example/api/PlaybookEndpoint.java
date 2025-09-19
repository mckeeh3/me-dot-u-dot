package com.example.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.application.PlaybookJournalView;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/playbook")
public class PlaybookEndpoint {
  static final Logger log = LoggerFactory.getLogger(PlaybookEndpoint.class);
  final ComponentClient componentClient;

  public PlaybookEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/get-journal-by-agent-id-down")
  public PlaybookJournalView.Journals getJournalByAgentIdDown(PlaybookJournalView.GetByAgentIdDownRequest request) {
    log.debug("Get journal down by agent id: {}", request);

    var queryRequest = new PlaybookJournalView.GetByAgentIdDownRequest(request.agentId(), request.sequenceId());

    return componentClient
        .forView()
        .method(PlaybookJournalView::getByAgentIdDown)
        .invoke(queryRequest);
  }

  @Post("/get-journal-by-agent-id-up")
  public PlaybookJournalView.Journals getJournalByAgentIdUp(PlaybookJournalView.GetByAgentIdUpRequest request) {
    log.debug("Get journal up by agent id: {}", request);

    var queryRequest = new PlaybookJournalView.GetByAgentIdUpRequest(request.agentId(), request.sequenceId());

    return componentClient
        .forView()
        .method(PlaybookJournalView::getByAgentIdUp)
        .invoke(queryRequest);
  }

  @Post("/get-journal-by-agent-id-and-sequence")
  public PlaybookJournalView.Journals getJournalByAgentIdAndSequence(PlaybookJournalView.GetByAgentIdAndSequenceRequest request) {
    log.debug("Get journal by agent id and sequence: {}", request);

    return componentClient.forView()
        .method(PlaybookJournalView::getByAgentIdAndSequence)
        .invoke(request);
  }
}
