package com.example.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.application.GameActionLogEntity;
import com.example.application.GameActionLogView;
import com.example.domain.GameActionLog;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.client.ComponentClient;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/game-action-log")
public class GameActionLogEndpoint {
  static final Logger log = LoggerFactory.getLogger(GameActionLogEndpoint.class);
  final ComponentClient componentClient;

  public GameActionLogEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Get("/get-logs-by-game")
  public GameActionLogView.Logs getLogsByGame(GameActionLogView.GetLogsByGameRequest request) {
    log.debug("Get logs by game: {}", request);

    return componentClient.forView()
        .method(GameActionLogView::getLogsByGame)
        .invoke(request);
  }

  @Get("/get-log-by-id")
  public GameActionLog.State getLogById(String logMessageId) {
    log.debug("Get log by id: {}", logMessageId);

    return componentClient.forKeyValueEntity(logMessageId)
        .method(GameActionLogEntity::getState)
        .invoke();
  }
}
