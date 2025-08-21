package com.example.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.EventSourcedTestKit;
import com.example.domain.PlaybookJournal;

public class PlaybookJournalEntityTest {

  @Test
  void testCreatePlaybookJournal() {
    var testKit = EventSourcedTestKit.of(PlaybookJournalEntity::new);
    var agentId = "test-agent";
    var sequenceId = 1L;
    var instructions = "test-instructions";
    var updatedAt = Instant.now();

    var command = new PlaybookJournal.Command.CreatePlaybookJournal(agentId, sequenceId, instructions, updatedAt);
    var result = testKit.method(PlaybookJournalEntity::createPlaybookJournal).invoke(command);
    assertTrue(result.isReply());

    var event = result.getNextEventOfType(PlaybookJournal.Event.PlaybookJournalCreated.class);
    assertEquals(agentId, event.agentId());
    assertEquals(sequenceId, event.sequenceId());
    assertEquals(instructions, event.instructions());
    assertEquals(updatedAt, event.updatedAt());

    var state = testKit.getState();
    assertEquals(agentId, state.agentId());
    assertEquals(sequenceId, state.sequenceId());
    assertEquals(instructions, state.instructions());
    assertEquals(updatedAt, state.updatedAt());
  }

  @Test
  void testGetPlaybookJournal() {
    var testKit = EventSourcedTestKit.of(PlaybookJournalEntity::new);
    var agentId = "test-agent";
    var sequenceId = 1L;
    var instructions = "test-instructions";
    var updatedAt = Instant.now();

    {
      var command = new PlaybookJournal.Command.CreatePlaybookJournal(agentId, sequenceId, instructions, updatedAt);
      var result = testKit.method(PlaybookJournalEntity::createPlaybookJournal).invoke(command);
      assertTrue(result.isReply());

      var state = testKit.getState();
      assertEquals(agentId, state.agentId());
      assertEquals(sequenceId, state.sequenceId());
      assertEquals(instructions, state.instructions());
      assertEquals(updatedAt, state.updatedAt());
    }

    {
      var result = testKit.method(PlaybookJournalEntity::getState).invoke();
      assertTrue(result.isReply());
      assertEquals(testKit.getState(), result.getReply());

      var json = JsonSupport.encodeToString(testKit.getState());
      assertTrue(json.contains(agentId));
      assertTrue(json.contains(String.valueOf(sequenceId)));
      assertTrue(json.contains(instructions));
      assertTrue(json.contains(updatedAt.toString()));
    }
  }
}
