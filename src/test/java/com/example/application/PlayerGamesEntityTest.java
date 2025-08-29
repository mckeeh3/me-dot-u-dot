package com.example.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.domain.PlayerGames;
import com.example.domain.PlayerGames.GameStats;
import com.example.domain.PlayerGames.State;

import akka.javasdk.testkit.EventSourcedResult;
import akka.javasdk.testkit.EventSourcedTestKit;

public class PlayerGamesEntityTest {

  @Test
  void testAddGameToBranch() {
    var testKit = EventSourcedTestKit.of(PlayerGamesEntity::new);
    var stats = new GameStats(1, 1, 1, 1);
    var command = new PlayerGames.Command.AddGameToBranch("branch1", "player1", "game1", Optional.empty(), stats);
    var result = testKit.method(PlayerGamesEntity::addGameToBranch).invoke(command);
    assertTrue(result.isReply());

    var event = result.getNextEventOfType(PlayerGames.Event.GameAdded.class);
    assertEquals(command.branchId(), event.branchId());
    assertEquals(command.playerId(), event.playerId());
    assertEquals(command.gameId(), event.gameId());
    assertEquals(command.parentBranchId(), event.parentBranchId());
    assertEquals(command.stats(), event.stats());

    var state = testKit.getState();
    assertEquals(command.branchId(), state.branchId());
    assertEquals(command.playerId(), state.playerId());
    assertEquals(command.parentBranchId(), state.parentBranchId());
    assertEquals(command.stats(), state.reduceStats());
    assertEquals(10, state.subBranches().size());
    assertEquals(1, state.leaves().size());
    assertTrue(state.leavesContainsLeaf(command.playerId(), command.gameId()));
  }

  @Test
  void testFillBranchToOverflow() {
    var testKit = EventSourcedTestKit.of(PlayerGamesEntity::new);
    var branchId = "branch1";
    var playerId = "player1";
    var stats = new GameStats(1, 1, 1, 1);
    var statsExpected = new GameStats(10, 10, 10, 10);

    addGame(testKit, branchId, playerId, "game1", stats);
    addGame(testKit, branchId, playerId, "game2", stats);
    addGame(testKit, branchId, playerId, "game3", stats);
    addGame(testKit, branchId, playerId, "game4", stats);
    addGame(testKit, branchId, playerId, "game5", stats);
    addGame(testKit, branchId, playerId, "game6", stats);
    addGame(testKit, branchId, playerId, "game7", stats);
    addGame(testKit, branchId, playerId, "game8", stats);
    addGame(testKit, branchId, playerId, "game9", stats);
    addGame(testKit, branchId, playerId, "game10", stats);

    {
      var state = testKit.getState();
      assertEquals(branchId, state.branchId());
      assertEquals(playerId, state.playerId());
      assertEquals(Optional.empty(), state.parentBranchId());
      assertEquals(statsExpected, state.reduceStats());
      assertTrue(state.leavesContainsLeaf(playerId, "game10"));
      assertTrue(state.subBranches().size() == 10);
      assertTrue(state.leaves().size() == 10);
    }

    var result = addGame(testKit, branchId, playerId, "game11", stats);
    assertTrue(result.isReply());

    var event = result.getNextEventOfType(PlayerGames.Event.DelegatedGameToSubBranch.class);
    assertNotNull(event.subBranchId());
    assertEquals(playerId, event.playerId());
    assertEquals(branchId, event.parentBranchId().get());
    assertEquals(stats, event.stats());

    var state = testKit.getState();
    assertEquals(branchId, state.branchId());
    assertEquals(playerId, state.playerId());
    assertEquals(statsExpected, state.reduceStats());
    assertFalse(state.leavesContainsLeaf(playerId, "game11"));
  }

  @Test
  void testParentAndSubBranchStatsUpdated() {
    var testKitParent = EventSourcedTestKit.of(PlayerGamesEntity::new);
    var testKitSub = EventSourcedTestKit.of(PlayerGamesEntity::new);
    var branchId = "branch1";
    var playerId = "player1";
    var stats = new GameStats(1, 1, 1, 1);

    addGame(testKitParent, branchId, playerId, "game1", stats);
    addGame(testKitParent, branchId, playerId, "game2", stats);
    addGame(testKitParent, branchId, playerId, "game3", stats);
    addGame(testKitParent, branchId, playerId, "game4", stats);
    addGame(testKitParent, branchId, playerId, "game5", stats);
    addGame(testKitParent, branchId, playerId, "game6", stats);
    addGame(testKitParent, branchId, playerId, "game7", stats);
    addGame(testKitParent, branchId, playerId, "game8", stats);
    addGame(testKitParent, branchId, playerId, "game9", stats);
    addGame(testKitParent, branchId, playerId, "game10", stats);

    var stateBefore = testKitParent.getState();

    // Create sub branch for game11
    var result = addGame(testKitParent, branchId, playerId, "game11", stats);
    assertTrue(result.isReply());

    var event = result.getNextEventOfType(PlayerGames.Event.DelegatedGameToSubBranch.class);

    var command = new PlayerGames.Command.AddGameToBranch(
        event.subBranchId(),
        event.playerId(),
        event.gameId(),
        event.parentBranchId(),
        event.stats());
    var resultSub = testKitSub.method(PlayerGamesEntity::addGameToBranch).invoke(command);
    assertTrue(resultSub.isReply());

    var eventSub = resultSub.getNextEventOfType(PlayerGames.Event.GameAdded.class);
    assertEquals(command.branchId(), eventSub.branchId());
    assertEquals(command.playerId(), eventSub.playerId());
    assertEquals(command.gameId(), eventSub.gameId());
    assertEquals(command.parentBranchId(), eventSub.parentBranchId());
    assertEquals(command.stats(), eventSub.stats());

    var state = testKitSub.getState();
    assertEquals(command.branchId(), state.branchId());
    assertEquals(command.playerId(), state.playerId());
    assertEquals(command.parentBranchId(), state.parentBranchId());
    assertEquals(command.stats(), state.reduceStats());
    assertEquals(1, state.leaves().size());
    assertTrue(state.leavesContainsLeaf(command.playerId(), command.gameId()));

    assertEquals(testKitParent.getState(), stateBefore); // state should not be changed when game is delegated to sub branch
  }

  static EventSourcedResult<State> addGame(EventSourcedTestKit<PlayerGames.State, PlayerGames.Event, PlayerGamesEntity> testKit, String branchId, String playerId, String gameId, GameStats stats) {
    var command = new PlayerGames.Command.AddGameToBranch(branchId, playerId, gameId, Optional.empty(), stats);
    return testKit.method(PlayerGamesEntity::addGameToBranch).invoke(command);
  }
}
