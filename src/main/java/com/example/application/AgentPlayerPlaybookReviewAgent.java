package com.example.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.DotGame;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.AgentContext;
import akka.javasdk.agent.JsonParsingException;
import akka.javasdk.agent.ModelException;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.agent.ModelTimeoutException;
import akka.javasdk.agent.RateLimitException;
import akka.javasdk.agent.ToolCallExecutionException;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;

@Component(id = "agent-player-playbook-review-agent")
public class AgentPlayerPlaybookReviewAgent extends Agent {
  static final Logger log = LoggerFactory.getLogger(AgentPlayerPlaybookReviewAgent.class);
  final ComponentClient componentClient;
  final String sessionId;
  final GameActionLogger gameLog;
  final List<Object> functionTools;

  public AgentPlayerPlaybookReviewAgent(ComponentClient componentClient, AgentContext agentContext) {
    this.componentClient = componentClient;
    this.sessionId = agentContext.sessionId();
    this.gameLog = new GameActionLogger(componentClient);
    this.functionTools = List.of(
        new PlaybookTools(componentClient));
  }

  public Effect<String> playbookReview(PlaybookReviewPrompt prompt) {
    var promptFormatted = prompt.toPrompt();

    log.debug("SessionId: {}\n_PlaybookReviewPrompt: {}", sessionId, prompt);
    gameLog.logModelPrompt(prompt.gameId, prompt.agent().id(), promptFormatted);

    return effects()
        .model(ModelProvider.fromConfig("ai-agent-model-" + prompt.agent().model()))
        // .model(ModelProvider.custom(new GeminiCustomModelProvider()))
        .tools(functionTools)
        .systemMessage(systemPrompt)
        .userMessage(promptFormatted)
        .onFailure(e -> handleError(prompt, e))
        .thenReply();
  }

  String handleError(PlaybookReviewPrompt prompt, Throwable exception) {
    return switch (exception) {
      case ModelException e -> tryAgain(prompt, e);
      case RateLimitException e -> throwException(prompt, e);
      case ModelTimeoutException e -> tryAgain(prompt, e);
      case ToolCallExecutionException e -> tryAgain(prompt, e);
      case JsonParsingException e -> tryAgain(prompt, e);
      case NullPointerException e -> nullResponse(e);
      default -> throwException(prompt, exception);
    };
  }

  String nullResponse(Throwable exception) {
    log.debug("SessionId: {}\n_Null model response, treated as no change needed\n_exception: {}", sessionId, exception.getMessage());
    return "";
  }

  String tryAgain(PlaybookReviewPrompt prompt, Throwable exception) {
    throw new TryAgainException(prompt, exception);
  }

  String throwException(PlaybookReviewPrompt prompt, Throwable exception) {
    throw new RuntimeException(exception);
  }

  static final String systemPrompt = """
      THE GAME
      This is a two-player, turn-by-turn, 2D board strategy game. Players take turns claiming squares on the board.
      The objective is to make scoring moves that result in points. Players must balance offensive moves (scoring points)
      with defensive moves (preventing the opponent from scoring points). Games end when a player wins, when there is a draw,
      or when a game is cancelled.

      YOUR ROLE
      You are a playbook maintenance agent. Your critical role is to maintain and evolve the player's playbook based on experience
      from each game. This is a critical self-learning step that directly impacts the player's ability to improve and win future games.
      You will receive a game review created by the post-game review agent, and you must use this review along with the current playbook
      to maintain the best possible playbook contents.

      THE PLAYBOOK
      The playbook is a unique document for each player. It is used during active gameplay when the player is making moves.
      The playbook serves as the player's tactical knowledge base and strategic guide. It contains:
      - Rules and mechanics of the game
      - Scoring patterns and move types
      - Offensive strategies (how to score points)
      - Defensive strategies (how to prevent opponent scoring)
      - Multi-move strategies and sequences
      - Lessons learned from previous games

      The playbook starts empty. When it is empty, you MUST create the initial version based on the game review.
      In subsequent games, you are responsible for maintaining and improving the playbook by incorporating new learnings
      while preserving valuable existing content.

      CRITICAL WORKFLOW
      You MUST follow this exact workflow:
      1. Call PlaybookTools_readPlaybook to retrieve the current playbook contents.
         - The playbook may be empty (initially) or contain existing tactical knowledge.
         - You must read it first to understand what is already documented.

      2. Review the provided game review thoroughly.
         - The game review contains detailed analysis of the completed game.
         - It includes critical moves, missed opportunities, scoring patterns, and strategic discoveries.
         - Use this review to identify what should be added, updated, or refined in the playbook.

      3. Revise the playbook as a complete document.
         - Review the current playbook content and the game review together.
         - Determine what needs to be added, updated, or removed.
         - Create a complete, revised playbook document that:
           * Preserves valuable existing content that remains relevant
           * Incorporates new learnings from the game review
           * Removes or updates outdated or incorrect information
           * Organizes content for easy use during gameplay

      4. Call PlaybookTools_writePlaybook to write the revised playbook.
         - CRITICAL: The write playbook tool COMPLETELY REPLACES the entire playbook document.
         - You must write the complete revised playbook, not just changes or additions.
         - If you don't include existing content in your write, it will be lost.

      5. Respond with your response text.

      HANDLING EMPTY PLAYBOOK
      When the playbook is empty, you MUST create the initial playbook version. Use the game review to identify:
      - Scoring move types and patterns discovered in the game
      - Offensive strategies that worked
      - Defensive strategies that worked
      - Multi-move sequences that led to scoring
      - Any other tactical knowledge that would be useful for future gameplay
      Then write a complete initial playbook based on these learnings.

      MAINTAINING EXISTING PLAYBOOK
      When the playbook already contains content, your responsibility is to maintain the best possible playbook by:
      - Adding new learnings from the game review
      - Updating existing content with new insights or corrections
      - Removing information that is no longer relevant or has been proven incorrect
      - Organizing and structuring content for maximum utility during gameplay
      - Preserving valuable foundational knowledge while evolving based on new experience

      IMPORTANT REMINDERS
      • You MUST read the current playbook before writing to avoid losing existing content.
      • The write playbook tool completely replaces the entire document—you must write the complete revised playbook.
      • When the playbook is empty, you MUST create the initial version.
      • Use the game review to identify what should be added or updated in the playbook.
      • Focus on maintaining the best possible playbook contents for future gameplay.
      • Do not ask for user input—the environment does not provide interactive users.

      VERIFY YOUR WORK
      Before responding with your response text, verify your work by:
      - Reading the current playbook to ensure you didn't lose any existing content
      - Reviewing the game review to ensure you incorporated all relevant learnings
      - Checking that you wrote the complete revised playbook
      - If you are not confident in your work, you can try again.
      - If you are confident in your work, respond with your response text.
      """.stripIndent();

  record PlaybookReviewPrompt(String sessionId, String gameId, DotGame.Player agent, String gameReview, boolean isRetry) {
    static PlaybookReviewPrompt with(String sessionId, String gameId, DotGame.Player agent, String gameReview) {
      return new PlaybookReviewPrompt(sessionId, gameId, agent, gameReview, false);
    }

    static PlaybookReviewPrompt withRetry() {
      return new PlaybookReviewPrompt("", "", null, "", true);
    }

    String toPrompt() {
      if (isRetry()) {
        return "Try again, you must update the playbook when it is empty";
      }

      return """
          PLAYBOOK REVIEW
          Game Id: %s | Agent Id: %s

          The game is over. Use the provided game review to revise your playbook.

          <GAME_REVIEW>
          %s
          </GAME_REVIEW>
          """
          .formatted(gameId(), agent().id(), gameReview);
    }
  }

  public class TryAgainException extends RuntimeException {
    public TryAgainException(PlaybookReviewPrompt prompt, Throwable cause) {
      super("Try again, possible recoverable agent error, agent: %s, agent error: %s".formatted(prompt.agent().id(), cause.getMessage()));
    }
  }
}
