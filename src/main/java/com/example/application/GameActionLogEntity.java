package com.example.application;

import static akka.Done.done;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.GameActionLog;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;

@ComponentId("agent-log-entity")
public class GameActionLogEntity extends KeyValueEntity<GameActionLog.State> {
  static final Logger log = LoggerFactory.getLogger(GameActionLogEntity.class);
  String entityId;

  public GameActionLogEntity(KeyValueEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public GameActionLog.State emptyState() {
    return GameActionLog.State.empty();
  }

  public Effect<Done> createAgentLog(GameActionLog.Command.CreateAgentLog command) {
    log.debug("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .updateState(currentState().onCommand(command))
        .thenReply(done());
  }

  public ReadOnlyEffect<GameActionLog.State> getState() {
    log.debug("EntityId: {}\n_State: {}", entityId, currentState());

    if (currentState().isEmpty()) {
      return effects().error("Agent log '%s' not found".formatted(entityId));
    }

    return effects().reply(currentState());
  }
}
