package com.example.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.application.AgentPlayerWorkflow;
import com.example.application.GameActionLogEntity;
import com.example.application.GameActionLogView;
import com.example.domain.AgentPlayer;
import com.example.domain.GameActionLog;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/game-action-log")
public class GameActionLogEndpoint {
  static final Logger log = LoggerFactory.getLogger(GameActionLogEndpoint.class);
  final ComponentClient componentClient;

  public GameActionLogEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/get-logs-by-game")
  public GameActionLogView.Logs getLogsByGame(GameActionLogView.GetLogsByGameRequest request) {
    log.debug("Get logs by game: {}", request);

    return componentClient.forView()
        .method(GameActionLogView::getLogsByGame)
        .invoke(request);
  }

  @Get("/get-log-by-id/{logMessageId}")
  public GameActionLog.State getLogById(String logMessageId) {
    log.debug("Get log by id: {}", logMessageId);

    return componentClient.forKeyValueEntity(logMessageId)
        .method(GameActionLogEntity::getState)
        .invoke();
  }

  @Get("/workflow-step-stream/{gameId}/{agentId}")
  public HttpResponse workflowStepStream(String gameId, String agentId) {
    var workflowId = AgentPlayer.sessionId(gameId, agentId);
    log.debug("Workflow step stream, workflowId: {}", workflowId);

    return HttpResponses.serverSentEvents(
        componentClient
            .forWorkflow(workflowId)
            .notificationStream(AgentPlayerWorkflow::stepStream)
            .source());
  }
}
