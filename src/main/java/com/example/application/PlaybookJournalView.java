package com.example.application;

import java.time.Instant;
import java.util.List;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.example.domain.PlaybookJournal;

@ComponentId("playbook-journal-view")
public class PlaybookJournalView extends View {

  @Query("""
      SELECT * AS journals
        FROM playbook_journal_view
       WHERE agentId = :agentId
       ORDER BY sequenceId DESC
       LIMIT :limit OFFSET :offset
      """)
  public QueryEffect<JournalsPage> getByAgentId(GetByAgentIdRequest request) {
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

  public record GetByAgentIdRequest(String agentId, long limit, long offset) {}

  public record JournalsPage(List<JournalRow> journals) {}
}
