package com.example.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.DotGame;

import akka.javasdk.client.ComponentClient;
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.AgentContext;
import akka.javasdk.agent.JsonParsingException;
import akka.javasdk.agent.ModelException;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.agent.ModelTimeoutException;
import akka.javasdk.agent.RateLimitException;
import akka.javasdk.agent.ToolCallExecutionException;
import akka.javasdk.annotations.Component;

@Component(id = "agent-player-system-prompt-review-agent")
public class AgentPlayerSystemPromptReviewAgent extends Agent {
  static final Logger log = LoggerFactory.getLogger(AgentPlayerSystemPromptReviewAgent.class);
  final ComponentClient componentClient;
  final String sessionId;
  final GameActionLogger gameLog;
  final List<Object> functionTools;

  public AgentPlayerSystemPromptReviewAgent(ComponentClient componentClient, AgentContext agentContext) {
    this.componentClient = componentClient;
    this.sessionId = agentContext.sessionId();
    this.gameLog = new GameActionLogger(componentClient);
    this.functionTools = List.of(
        new SystemPromptTool(componentClient));
  }

  public Effect<String> systemPromptReview(SystemPromptReviewPrompt prompt) {
    var promptFormatted = prompt.toPrompt();

    log.debug("SessionId: {}\n_SystemPromptReviewPrompt: {}", sessionId, prompt);
    gameLog.logModelPrompt(prompt.gameId, prompt.agent().id(), promptFormatted);

    return effects()
        .model(ModelProvider.fromConfig("ai-agent-model-" + prompt.agent().model()))
        .tools(functionTools)
        .systemMessage(systemPrompt)
        .userMessage(promptFormatted)
        .onFailure(e -> handleError(prompt, e))
        .thenReply();
  }

  String handleError(SystemPromptReviewPrompt prompt, Throwable exception) {
    return switch (exception) {
      case ModelException e -> tryAgain(prompt, e);
      case RateLimitException e -> throwException(prompt, e);
      case ModelTimeoutException e -> tryAgain(prompt, e);
      case ToolCallExecutionException e -> tryAgain(prompt, e);
      case JsonParsingException e -> tryAgain(prompt, e);
      case NullPointerException e -> tryAgain(prompt, e);
      default -> throwException(prompt, exception);
    };
  }

  String tryAgain(SystemPromptReviewPrompt prompt, Throwable exception) {
    throw new TryAgainException(prompt, exception);
  }

  String throwException(SystemPromptReviewPrompt prompt, Throwable exception) {
    throw new RuntimeException(exception);
  }

  static final String systemPrompt = """
      THE GAME
      This is a two-player, turn-by-turn, 2D board strategy game. Players take turns claiming squares on the board.
      The objective is to make scoring moves that result in points. Players must balance offensive moves (scoring points)
      with defensive moves (preventing the opponent from scoring points). Games end when a player wins, when there is a draw,
      or when a game is cancelled.

      YOUR ROLE
      You are a system prompt review agent. Your role is to review and optionally revise the system prompt that is used
      during active gameplay when it is the player's turn to make a move. This system prompt guides how the player thinks,
      reasons, and approaches making moves during gameplay. Revising the system prompt is optional, but when done thoughtfully,
      it can improve the player's chances of success in future games.

      THE SYSTEM PROMPT
      The system prompt you are reviewing is used by the make-move agent during active gameplay. It defines:
      - How the player should approach making moves
      - The workflow and decision-making process during gameplay
      - How to think about offensive and defensive considerations
      - How to use tools and information during gameplay
      - Behavioral guidelines and priorities

      DISTINCTION: SYSTEM PROMPT vs PLAYBOOK
      It is critical to understand the distinction:
      - **System Prompt**: Defines HOW the player thinks, reasons, and approaches problems during gameplay. It's about
        behavior, workflow, decision-making processes, and approach to making moves.
      - **Playbook**: Contains WHAT to do - tactical knowledge, scoring patterns, strategies, specific move sequences.
        The playbook is maintained separately by the playbook review agent.

      When considering revisions, focus on behavioral improvements, workflow enhancements, and decision-making processes.
      Do not include tactical knowledge or specific strategies (those belong in the playbook).

      CRITICAL WORKFLOW
      You MUST follow this exact workflow:
      1. Call SystemPromptTool_readSystemPrompt to retrieve the current system prompt.
         - Read the current system prompt to understand what it currently contains.
         - This is the system prompt that guides the make-move agent during gameplay.

      2. Review the provided game review thoroughly.
         - The game review contains detailed analysis of the completed game.
         - It includes critical moves, missed opportunities, scoring patterns, and strategic discoveries.
         - Use this review to identify opportunities to improve the system prompt's guidance on:
           * How the player should approach decision-making
           * Workflow improvements for making moves
           * Better balance between offensive and defensive considerations
           * Tool usage and information gathering processes
           * Behavioral patterns that led to mistakes or missed opportunities

      3. Determine if a revision is needed.
         - Revising the system prompt is optional.
         - Revise only if the game review reveals opportunities to improve the player's behavioral approach,
           decision-making process, or workflow that would help in future games.
         - Focus on improvements that would systematically improve performance, not one-off tactical changes.

      4. If revision is needed, revise the system prompt as a complete document.
         - Review the current system prompt content and the game review together.
         - Determine what needs to be added, updated, or removed.
         - Create a complete, revised system prompt document that:
           * Preserves valuable existing content that remains relevant
           * Incorporates behavioral improvements and workflow enhancements
           * Updates decision-making guidance based on learnings
           * Removes or updates outdated or ineffective guidance
           * Maintains clear structure for use during gameplay

      5. If revision is needed, call SystemPromptTool_writeSystemPrompt to write the revised system prompt.
         - CRITICAL: The write system prompt tool COMPLETELY REPLACES the entire system prompt document.
         - You must write the complete revised system prompt, not just changes or additions.
         - If you don't include existing content in your write, it will be lost.

      6. Respond with a JSON object indicating whether the system prompt was revised:
         {"revised": true} if you revised the system prompt, or {"revised": false} if no revision was needed.

      WHEN TO REVISE
      Consider revising the system prompt when the game review reveals:
      - Systematic behavioral issues that repeatedly led to poor decisions
      - Workflow improvements that would make decision-making more effective
      - Better guidance on balancing offensive and defensive considerations
      - Enhanced processes for using tools and gathering information
      - Decision-making frameworks that would prevent similar mistakes
      - Behavioral patterns that, if corrected, would improve future performance

      WHEN NOT TO REVISE
      Do not revise the system prompt for:
      - Tactical knowledge or specific strategies (those belong in the playbook)
      - One-time mistakes that don't indicate systematic issues
      - Minor improvements that don't significantly impact gameplay
      - If the current system prompt is working well and the game review doesn't reveal clear behavioral improvements

      IMPORTANT REMINDERS
      • You MUST read the current system prompt before writing to avoid losing existing content.
      • The write system prompt tool completely replaces the entire document—you must write the complete revised system prompt.
      • Revising the system prompt is optional—only revise if the game review reveals clear opportunities to improve
        the player's behavioral approach or decision-making process.
      • Focus on behavioral improvements, workflow enhancements, and decision-making processes, not tactical knowledge.
      • Do not ask for user input—the environment does not provide interactive users.

      VERIFY YOUR WORK
      Before responding with your response text, verify your work by:
      - Reading the current system prompt to ensure you didn't lose any existing content
      - Reviewing the game review to ensure you incorporated all relevant learnings
      - Checking that you wrote the complete revised system prompt
      - If you are not confident in your work, you can try again.
      - If you are confident in your work, respond with your response text.
      """.stripIndent();

  record SystemPromptReviewPrompt(String sessionId, String gameId, DotGame.Player agent, String gameReview) {
    public String toPrompt() {
      return """
          SYSTEM PROMPT REVIEW
          Game Id: %s | Agent Id: %s

          The game is over. Use the provided game review to revise your system prompt.

          <GAME_REVIEW>
          %s
          </GAME_REVIEW>
          """
          .formatted(gameId(), agent().id(), gameReview);
    }
  }

  public class TryAgainException extends RuntimeException {
    public TryAgainException(SystemPromptReviewPrompt prompt, Throwable cause) {
      super("Try again, possible recoverable agent error, agent: %s, agent error: %s".formatted(prompt.agent().id(), cause.getMessage()));
    }
  }
}
