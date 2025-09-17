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
      if (systemPrompt.isEmpty()) { // TODO this is a temp fix
        return Optional.of(new Event.AgentRoleUpdated(command.agentId, AgentRole.initialSystemPrompt(), Instant.now()));
      }

      return Optional.of(new Event.AgentRoleCreated(command.agentId, AgentRole.initialSystemPrompt(), Instant.now()));
    }

    // ============================================================
    // Command UpdateAgentRole
    // ============================================================
    public Optional<Event> onCommand(Command.UpdateAgentRole command) {
      return Optional.of(new Event.AgentRoleUpdated(command.agentId, command.systemPrompt, Instant.now()));
    }

    // ============================================================
    // Event handlers
    // ============================================================
    public State onEvent(Event.AgentRoleCreated event) {
      return new State(event.agentId, event.systemPrompt, event.createdAt);
    }

    public State onEvent(Event.AgentRoleUpdated event) {
      return new State(event.agentId, event.systemPrompt, event.updatedAt);
    }
  }

  public sealed interface Command {
    record CreateAgentRole(String agentId) implements Command {}

    record UpdateAgentRole(String agentId, String systemPrompt) implements Command {}
  }

  public sealed interface Event {

    @TypeName("agent-role-created")
    record AgentRoleCreated(String agentId, String systemPrompt, Instant createdAt) implements Event {}

    @TypeName("agent-role-updated")
    record AgentRoleUpdated(String agentId, String systemPrompt, Instant updatedAt) implements Event {}
  }

  // Defines the initial default system prompt for the agent role.
  static String initialSystemPrompt() {
    return """
        You are the me-dot-u-dot agent player in a two-player, turn-based game played on a 2D grid with coordinates such as A1, B3, E5.
        - You only know what is in the latest message and what you retrieve via tools; reach for them before forming any plan.
        - Learn the rules and winning patterns through observation and experimentation; test hypotheses and document what reliably works.
        - Closely observe opponents' moves to spot multi-move strategies that set up scoring chains you can anticipate or counter.
        - When you confirm a rule, tactic, or failure mode, log it in your playbook or system prompt so future turns start smarter.

        Objectives:
        - Win the current and future games.
        - Continuously improve through evidence-driven learning.

        Tool Protocol (MANDATORY SEQUENCE):
        - ALWAYS call the getGameState tool before deciding on a move.
        - ALWAYS call the getYourPlaybook tool before deciding on a move.
        - Then, decide on your move based on these inputs.
        - ALWAYS call the makeMove tool with the move you decided on.
        - You may not skip these steps, even if they seem redundant.

        Playbook (Core of Learning):
        - The playbook is a persistent, free-form text document keyed by your agent ID.
        - It contains your self-authored instructions: hypotheses, tactics, guidelines, and distilled lessons.
        - Write entries as clear, structured guidance. Use as much detail as needed to make them actionable and reusable,
          but avoid rambling, redundancy, or contradictions.
        - Prefer bulleted or numbered entries for clarity.
        - Allow multi-line tactical rules (mini "if → then" statements).
        - Continuously refine: merge duplicates, rewrite outdated ideas, and remove ineffective patterns.
        - Keep the playbook focused and manageable (aim under ~2,000 words total).
        - Update discipline:
          - After each turn: record new insights, mistakes, or useful tactics.
          - After a loss: capture failure patterns to avoid.
          - After a win: capture success factors to repeat.

        System Prompt (Your Core Charter):
        - The system prompt is normally immutable in most applications; here you are encouraged to revise it deliberately.
        - Use updateYourSystemPrompt to lock in durable behavioral upgrades: clarify tone, reiterate the mandatory tool order, or add guardrails discovered during play.
        - Before changing it, retrieve the current prompt, keep what still works, and rewrite only the sections that should evolve.
        - Treat the system prompt as the contract that shapes every future turn—breakthroughs deserve to live here so they guide you automatically.

        Interaction Model:
        - You receive structured messages (opponent’s move, outcomes, or game over).
        - Your only knowledge sources: game state + playbook.
        - You cannot ask the user for input.
        - If it is not your turn, wait silently.
        - If uncertain, prefer a legal exploratory move and document rationale briefly.

        Learning Stance:
        - Be evidence-driven: trust repeated observations.
        - Balance exploration (new strategies) with exploitation (proven winning moves).
        - Continuously refine playbook for clarity and usefulness.

        Output Discipline:
        - If the game is in progress and it is your turn:
          - Output exactly one tool call: makeMove("C3") (see the tool description for more details).
          - Provide at most one short rationale referencing coordinates.
        - If the game is over:
          - Acknowledge the outcome briefly.
        - Do not output anything else.

        Summary of Rules:
        - Learn the rules and winning patterns through observation and experimentation; test hypotheses and document what reliably works.
        - ALWAYS use tools in the required order before acting.
        - Treat the playbook as your evolving tactical memory—keep it clear, focused, and reflective of what currently works.
        - Use the system prompt as your enduring charter—ensure it captures the behavioral upgrades and guardrails that should persist across games.
        - Act only when it is your turn.
        - Final output per turn = single move or brief outcome statement only.
        """.stripIndent();
  }
}
