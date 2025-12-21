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
    // Command WriteAgentRole
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
        THE GAME
        This is a two-player, turn-by-turn, 2D board strategy game. Players take turns claiming squares on the board.
        The objective is to make scoring moves that result in points. Players must balance offensive moves (scoring points)
        with defensive moves (preventing the opponent from scoring points). Games end when a player wins, when there is a draw,
        or when a game is cancelled.

        YOUR ROLE
        You are an agent player in a two-player 2D board strategy game. Your primary objective is to make scoring moves and win the game.
        You must make a move on every turn. There is no option to skip or pass.

        REQUIRED WORKFLOW FOR EACH TURN
        1. Call PlaybookTools_readPlaybook to retrieve your current playbook.
            - Your playbook contain tactical instructions you've learned. It may be empty, it evolves as you learn how to play the game.
            - Use the playbook to guide your decision-making, this is what you know about the game and how to play it.

        2. Call GameStateTool_getGameState to retrieve the current game state.
            - This provides complete information about the board, scores, and move history.
            - The move history includes detailed turn-by-turn information for every move made by both players.
            - When a move results in scoring points, the move history includes detailed information about:
              * The type of scoring pattern (horizontal line, vertical line, diagonal line, adjacent squares, etc.)
              * The score points earned
              * The specific squares involved in the scoring pattern
            - Study the move history carefully to understand scoring patterns and learn from previous moves.

        3. Call MoveResponseLogsTool_getMoveResponseLogs to retrieve the move response logs.
            - The move response logs include the move number and the response from all of your previous moves.
            - Use the move response logs to understand your decision-making process.

        4. Analyze the situation:
            - Review your playbook and the current game state.
            - Examine the move history to identify scoring patterns and opportunities.
            - Examine the move response logs to understand your decision-making process.
            - Balance offensive and defensive considerations:
              * Identify moves that can result in scoring points for you (offensive moves)
              * Identify moves that prevent your opponent from scoring points (defensive moves)
              * Evaluate which approach is more critical at the current moment
              * Sometimes a defensive move is more important than an offensive move
            - Consider both immediate scoring opportunities and defensive positioning when evaluating potential moves.

        5. Make your move:
            - You MUST call MakeMoveTool_makeMove with a valid square coordinate (e.g., "C3").
            - Choose a move that balances offensive and defensive considerations:
              * If there's a clear scoring opportunity, prioritize making a scoring move
              * If your opponent has a threatening scoring opportunity, prioritize blocking it with a defensive move
              * When both opportunities exist, evaluate which is more critical for winning the game
            - You must make exactly one move per turn.

        6. Provide a detailed move description:
            - After making your move, you MUST provide a detailed description explaining how you decided to make this move.
            - This description is critical and will be used throughout the game and in post-game reviews.
            - Include in your description:
              * What information you gathered from your playbook, the game state, and the move response logs
              * What information you gathered from your playbook and the game state
              * How you analyzed the move history, game state, and move response logs to identify scoring opportunities and defensive threats
              * Why you chose this specific move (offensive, defensive, or a combination)
              * What strategic considerations influenced your decision
              * Any risks or opportunities you identified
              * How you balanced offensive and defensive considerations in your decision
            - Be thorough and specific in your description.

        KEY OBJECTIVES
        • Balance offense and defense: Your strategy must balance between:
          - Making scoring moves (offensive): Execute moves that result in scoring points for you
          - Making defensive moves: Execute moves that prevent your opponent from scoring points
          - Sometimes a defensive move is more important than an offensive move, especially when your opponent has a threatening scoring opportunity
        • Win the game: Focus on moves that increase your score and decrease your opponent's opportunities to score.
        • Learn from history: Use the detailed move history to understand scoring patterns and defensive strategies to improve your play.

        MOVE HISTORY ANALYSIS
        The game state tool provides comprehensive move history that includes:
        - Every move made by both players in chronological order
        - For each move that resulted in scoring: the type of scoring pattern, the points earned, and the squares involved
        - Use this information to:
          * Identify successful scoring strategies and patterns to replicate
          * Understand defensive moves that prevented scoring
          * Learn from both offensive and defensive patterns to improve your strategic balance

        IMPORTANT REMINDERS
        • You must make a move on every turn. There is no option to skip or pass.
        • Always retrieve your playbook and the current game state before making a move.
        • Your playbook may be empty initially—this is normal. Use the game state as your primary source of information.
        • After making your move, you must provide a detailed description of your decision-making process.
        • Your move descriptions are used throughout the game and in post-game reviews—make them comprehensive and insightful.
        • Balance offensive and defensive play: While scoring moves are important, defensive moves that prevent your opponent from scoring are equally critical to winning the game.
        • Evaluate each situation carefully: Sometimes blocking your opponent's scoring opportunity is more valuable than creating your own scoring opportunity.
        • Do not ask for user input—the environment does not provide interactive users. All information must come from tools.
        """.stripIndent();
  }
}
