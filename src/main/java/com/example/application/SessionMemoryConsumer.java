package com.example.application;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.agent.SessionHistory;
import akka.javasdk.agent.SessionMemoryEntity;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;

@Component(id = "session-memory-consumer")
@Consume.FromEventSourcedEntity(SessionMemoryEntity.class)
public class SessionMemoryConsumer extends Consumer {
  static final Logger log = LoggerFactory.getLogger(SessionMemoryConsumer.class);
  final ComponentClient componentClient;

  public SessionMemoryConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onEvent(SessionMemoryEntity.Event event) {
    if (!messageContext().hasLocalOrigin()) {
      log.debug("Ignoring event from other region: {}", event);
      return effects().done();
    }

    var entityId = messageContext().eventSubject().get();
    var sequenceId = messageContext().metadata().asCloudEvent().sequence().orElse(-1l);

    return switch (event) {
      case SessionMemoryEntity.Event.UserMessageAdded e -> onEvent(e, entityId, sequenceId);
      case SessionMemoryEntity.Event.AiMessageAdded e -> onEvent(e, entityId, sequenceId);
      case SessionMemoryEntity.Event.ToolResponseMessageAdded e -> onEvent(e, entityId, sequenceId);
      default -> {
        log.debug("EntityId: {}\n_Event: {}", entityId, event);
        yield effects().done();
      }
    };
  }

  Effect onEvent(SessionMemoryEntity.Event.UserMessageAdded event, String entityId, long sequenceId) {
    log.debug("EntityId: {}\n_SequenceId: {}\n_Timestamp: {}\n_UserMessage:\n_message: {}",
        entityId, sequenceId, event.timestamp(), event.message());

    var history = getHistory(entityId);
    history.messages().forEach(message -> {
      log.debug("EntityId: {}\n_SequenceId: {}\n_HistoryMessage: {}",
          entityId, sequenceId, message);
    });
    log.debug("EntityId: {}\n_SequenceId: {}\n_HistorySize: {}\n_History: {}",
        entityId, sequenceId, history.messages().size(), history.messages());
    return effects().done();
  }

  Effect onEvent(SessionMemoryEntity.Event.AiMessageAdded event, String entityId, long sequenceId) {
    var historySize = event.historySizeInBytes();
    var toolCallRequests = event.toolCallRequests();

    if (toolCallRequests.size() > 0) {
      log.debug("EntityId: {}\n_SequenceId: {}\n_HistorySize: {}\n_Timestamp: {}\n_AiMessage:\n_toolCallRequests({}):\n_toolCallRequests: {}",
          entityId, sequenceId, historySize, event.timestamp(), toolCallRequests.size(), event.toolCallRequests());
    } else {
      log.debug("EntityId: {}\n_SequenceId: {}\n_HistorySize: {}\n_Timestamp: {}\n_AiMessage:\n_Event: {}",
          entityId, sequenceId, historySize, event.timestamp(), event);
    }
    return effects().done();
  }

  Effect onEvent(SessionMemoryEntity.Event.ToolResponseMessageAdded event, String entityId, long sequenceId) {
    log.debug("EntityId: {}\n_SequenceId: {}\n_Timestamp: {}\n_ToolResponseMessage:\n_name: {}\n_content: {}",
        entityId, sequenceId, event.timestamp(), event.name(), event.content());
    return effects().done();
  }

  SessionHistory getHistory(String entityId) {
    var history = componentClient
        .forEventSourcedEntity(entityId)
        .method(SessionMemoryEntity::getHistory)
        .invoke(new SessionMemoryEntity.GetHistoryCmd(Optional.empty()));
    return history;
  }
}