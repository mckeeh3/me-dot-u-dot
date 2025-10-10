package com.example.application;

import java.time.Instant;
import java.util.List;

import com.example.domain.AgentRoleJournal;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

@Component(id = "agent-role-journal-view")
public class AgentRoleJournalView extends View {

  @Query("""
      SELECT * AS journals
        FROM agent_role_journal_view
       WHERE agentId = :agentId
       AND sequenceId < :sequenceId
       ORDER BY sequenceId DESC
       LIMIT 1
      """)
  public QueryEffect<Journals> getByAgentIdDown(GetByAgentIdDownRequest request) {
    return queryResult();
  }

  @Query("""
      SELECT * AS journals
        FROM agent_role_journal_view
       WHERE agentId = :agentId
       AND sequenceId > :sequenceId
       ORDER BY sequenceId ASC
       LIMIT 1
      """)
  public QueryEffect<Journals> getByAgentIdUp(GetByAgentIdUpRequest request) {
    return queryResult();
  }

  @Query("""
      SELECT * AS journals
        FROM agent_role_journal_view
       WHERE agentId = :agentId
       AND sequenceId = :sequenceId
       LIMIT 1
      """)
  public QueryEffect<Journals> getByAgentIdAndSequence(GetByAgentIdAndSequenceRequest request) {
    return queryResult();
  }

  @Consume.FromEventSourcedEntity(AgentRoleJournalEntity.class)
  public static class ByAgent extends TableUpdater<JournalRow> {

    public Effect<JournalRow> onEvent(AgentRoleJournal.Event event) {
      return switch (event) {
        case AgentRoleJournal.Event.AgentRoleJournalCreated e -> effects().updateRow(onEvent(e));
      };
    }

    private JournalRow onEvent(AgentRoleJournal.Event.AgentRoleJournalCreated e) {
      return new JournalRow(
          e.journalId(),
          e.agentId(),
          e.sequenceId(),
          e.systemPrompt(),
          e.updatedAt());
    }
  }

  public record JournalRow(
      String journalId,
      String agentId,
      long sequenceId,
      String systemPrompt,
      Instant updatedAt) {}

  public record GetByAgentIdRequest(String agentId) {}

  public record GetByAgentIdAndSequenceRequest(String agentId, long sequenceId) {}

  public record GetByAgentIdDownRequest(String agentId, long sequenceId) {}

  public record GetByAgentIdUpRequest(String agentId, long sequenceId) {}

  public record Journals(List<JournalRow> journals) {}
}
