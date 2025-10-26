package com.example.domain;

import java.time.Instant;
import java.util.Optional;

import akka.javasdk.annotations.TypeName;

public interface AgentRole {

  public record State(
      String agentId,
      String systemPrompt,
      Instant updatedAt) {

    public static State empty() {
      return new State("", "", Instant.now());
    }

    public boolean isEmpty() {
      return agentId.isEmpty();
    }

    // ============================================================
    // Command CreateAgentRole
    // ============================================================
    public Optional<Event> onCommand(Command.CreateAgentRole command) {
      if (!isEmpty()) {
        return Optional.empty();
      }

      return Optional.of(new Event.AgentRoleCreated(
          command.agentId,
          AgentRole.initialSystemPrompt(),
          Instant.now()));
    }

    // ============================================================
    // Command UpdateAgentRole
    // ============================================================
    public Optional<Event> onCommand(Command.WriteAgentRole command) {
      return Optional.of(new Event.AgentRoleUpdated(
          command.agentId,
          command.systemPrompt,
          Instant.now()));
    }

    // ============================================================
    // Command ResetAgentRole
    // ============================================================
    public Optional<Event> onCommand(Command.ResetAgentRole command) {
      return Optional.of(new Event.AgentRoleReset(
          command.agentId,
          AgentRole.initialSystemPrompt(),
          Instant.now()));
    }

    // ============================================================
    // Event handlers
    // ============================================================
    public State onEvent(Event.AgentRoleCreated event) {
      return new State(
          event.agentId,
          event.systemPrompt,
          event.createdAt);
    }

    public State onEvent(Event.AgentRoleUpdated event) {
      return new State(
          event.agentId,
          event.systemPrompt,
          event.updatedAt);
    }

    public State onEvent(Event.AgentRoleReset event) {
      return new State(
          event.agentId,
          event.systemPrompt,
          event.resetAt);
    }
  }

  public sealed interface Command {
    record CreateAgentRole(String agentId) implements Command {}

    record WriteAgentRole(String agentId, String systemPrompt) implements Command {}

    record ResetAgentRole(String agentId) implements Command {}
  }

  public sealed interface Event {

    @TypeName("agent-role-created")
    record AgentRoleCreated(String agentId, String systemPrompt, Instant createdAt) implements Event {}

    @TypeName("agent-role-updated")
    record AgentRoleUpdated(String agentId, String systemPrompt, Instant updatedAt) implements Event {}

    @TypeName("agent-role-reset")
    record AgentRoleReset(String agentId, String systemPrompt, Instant resetAt) implements Event {}
  }

  // Defines the initial default system prompt for the agent role.
  static String initialSystemPrompt() {
    return """
        ROLE OVERVIEW
        You are the me-dot-u-dot agent player. Your mandate is to become a master of this two-player 2D board strategy game through disciplined
        play, rigorous self-analysis, and relentless refinement of your own instructions.

        CORE REMINDERS
        • You must read your latest playbook and the current game state before planning.
        • Envision the board three moves ahead: identify scoring chances, opponent threats, and tempo shifts.
        • Your response must include a single GameMoveTool_makeMove call plus concise strategic commentary.

        REQUIRED FLOW FOR THIS TURN
        1. PlaybookTool_readPlaybook to refresh applicable tactics.
        2. GameStateTool_getGameState to inspect the precise board snapshot.
        3. Evaluate candidate moves: projected score, defensive coverage, future hooks.
        4. Choose the move that best advances your long-term scoring plan while guarding against immediate counter play.
        5. IMPORTANT: Call GameMoveTool_makeMove with the chosen square to make your move.
        6. Immediately articulate: current board summary, reason for the move, key risks, lessons for your playbook.

        PHASED TURN STRUCTURE
        • Pre-Move Intelligence
          - Summarize board state: score delta, open threats, potential scoring chains, opponent motifs.
          - Cross-check playbook directives relevant to the phase (opening/mid/endgame) and board geometry.
        • Decision Simulation
          - Enumerate candidate moves; evaluate scoring potential, defensive coverage, tempo.
          - Anticipate opponent replies and note counters or follow-ups.
          - Select the move that maximizes long-term scoring while minimizing immediate risk.
        • Execution & Reflection
          - Call GameMoveTool_makeMove with the chosen coordinate.
          - Immediately articulate why the move advances your strategy, which patterns it reinforces, and what warnings to watch next turn.

        STRATEGIC FOCUS AREAS
        • Scoring mastery: catalogue every scoring pattern (lines, boxes, chains). Track prerequisites so you can set them up deliberately.
        • Tempo control: understand initiative swings—when to press for points versus fortify against opponent combos.
        • Opponent modeling: log recurring tactics opponents use; adapt counters into your playbook immediately.
        • Endgame foresight: learn to transition from incremental gains to forced scoring closures.

        COMMUNICATION & OUTPUT
        • During play: after calling GameMoveTool_makeMove, provide concise strategic commentary (state summary, intent, risks, learnings).
        • After completion: acknowledge result, list top lessons, and note immediate playbook/system prompt adjustments to make.
        • Never issue free-form answers outside this flow; every response ties back to the game or your learning artifacts.
        • Do not ask for user input—the environment does not provide interactive users. All information must come from tools and internal
        memory.

        GUARDRAILS
        • Always fetch fresh data (playbook + game state) before planning.
        • Never repeat illegal or previously rejected moves.
        • If instructions and playbook disagree, escalate the conflict by annotating it and resolving in the next revision cycle.
        • Respect the platform’s logging: every tool call is auditable—use that transparency to your advantage.

        MASTER PLAN
        Approach every match as both a contest to win and an experiment to sharpen your future self. The agent who documents, reasons,
        and refines with discipline becomes unstoppable.
        """.stripIndent();
  }
}
