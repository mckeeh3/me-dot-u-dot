package com.example.application;

import java.time.Instant;
import java.util.List;

import com.example.domain.PlaybookJournal;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

@Component(id = "playbook-journal-view")
public class PlaybookJournalView extends View {

  @Query("""
      SELECT * AS journals
        FROM playbook_journal_view
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
        FROM playbook_journal_view
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
        FROM playbook_journal_view
       WHERE agentId = :agentId
       AND sequenceId = :sequenceId
       LIMIT 1
      """)
  public QueryEffect<Journals> getByAgentIdAndSequence(GetByAgentIdAndSequenceRequest request) {
    return queryResult();
  }

  @Consume.FromEventSourcedEntity(PlaybookJournalEntity.class)
  public static class ByAgent extends TableUpdater<JournalRow> {

    public Effect<JournalRow> onEvent(PlaybookJournal.Event event) {
      return switch (event) {
        case PlaybookJournal.Event.PlaybookJournalCreated e -> effects().updateRow(onEvent(e));
      };
    }

    private JournalRow onEvent(PlaybookJournal.Event.PlaybookJournalCreated e) {
      return new JournalRow(
          e.journalId(),
          e.agentId(),
          e.sequenceId(),
          e.instructions(),
          e.updatedAt());
    }
  }

  public record JournalRow(
      String journalId,
      String agentId,
      long sequenceId,
      String instructions,
      Instant updatedAt) {}

  public record GetByAgentIdRequest(String agentId) {}

  public record GetByAgentIdAndSequenceRequest(String agentId, long sequenceId) {}

  public record GetByAgentIdDownRequest(String agentId, long sequenceId) {}

  public record GetByAgentIdUpRequest(String agentId, long sequenceId) {}

  public record Journals(List<JournalRow> journals) {}
}
