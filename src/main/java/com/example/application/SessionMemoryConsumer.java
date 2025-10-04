package com.example.application;

import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.agent.SessionMemoryEntity;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@ComponentId("session-memory-consumer")
@Consume.FromEventSourcedEntity(SessionMemoryEntity.class)
public class SessionMemoryConsumer extends Consumer {
  static final Logger log = LoggerFactory.getLogger(SessionMemoryConsumer.class);

  public Effect onSessionMemoryEvent(SessionMemoryEntity.Event event) {
    var entityId = messageContext().eventSubject().get();
    var sequenceId = messageContext().metadata().asCloudEvent().sequence().orElse(-1l);
    var timestamp = (messageContext().metadata().asCloudEvent().time().orElse(ZonedDateTime.now())).toInstant();

    log.debug("EntityId: {}\n_SequenceId: {}\n_Timestamp: {}\n_Event: {}", entityId, sequenceId, timestamp, event.getClass().getSimpleName());

    return switch (event) {
      case SessionMemoryEntity.Event.UserMessageAdded e -> onEvent(e, entityId);
      case SessionMemoryEntity.Event.AiMessageAdded e -> onEvent(e, entityId);
      case SessionMemoryEntity.Event.ToolResponseMessageAdded e -> onEvent(e, entityId);
      default -> {
        log.debug("EntityId: {}\n_Event: {}", entityId, event);
        yield effects().done();
      }
    };
  }

  Effect onEvent(SessionMemoryEntity.Event.UserMessageAdded event, String entityId) {
    log.debug("EntityId: {}\n_UserMessage:\n_message: {}", entityId, event.message());
    return effects().done();
  }

  Effect onEvent(SessionMemoryEntity.Event.AiMessageAdded event, String entityId) {
    var toolCallRequests = event.toolCallRequests();
    if (toolCallRequests.size() > 0) {
      log.debug("EntityId: {}\n_AiMessage:\n_toolCallRequests({}):\n_message: {}", entityId, toolCallRequests.size(), event.message());
      toolCallRequests
          .forEach(toolCallRequest -> log.debug("EntityId: {}\n_toolCallRequest:\n_id: {}\n_name: {}\n_arguments: {}",
              entityId, toolCallRequest.id(), toolCallRequest.name(), toolCallRequest.arguments()));
    } else {
      log.debug("EntityId: {}\n_AiMessage:\n_Event: {}", entityId, event);
    }
    return effects().done();
  }

  Effect onEvent(SessionMemoryEntity.Event.ToolResponseMessageAdded event, String entityId) {
    log.debug("EntityId: {}\n_ToolResponseMessage:\n_name: {}\n_content: {}", entityId, event.name(), event.content());
    return effects().done();
  }
}