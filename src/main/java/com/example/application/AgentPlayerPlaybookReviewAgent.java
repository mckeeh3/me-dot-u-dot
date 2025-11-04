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
        new PlaybookTool(componentClient));
  }

  public Effect<PlaybookRevised> playbookReview(PlaybookReviewPrompt prompt) {
    var promptFormatted = prompt.toPrompt();

    log.debug("SessionId: {}\n_PlaybookReviewPrompt: {}", sessionId, prompt);
    gameLog.logModelPrompt(prompt.gameId, prompt.agent().id(), promptFormatted);

    return effects()
        .model(ModelProvider.fromConfig("ai-agent-model-" + prompt.agent().model()))
        .tools(functionTools)
        .systemMessage(systemPrompt)
        .userMessage(promptFormatted)
        .responseAs(PlaybookRevised.class)
        .onFailure(e -> handleError(prompt, e))
        .thenReply();
  }

  PlaybookRevised handleError(PlaybookReviewPrompt prompt, Throwable exception) {
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

  PlaybookRevised tryAgain(PlaybookReviewPrompt prompt, Throwable exception) {
    throw new TryAgainException(prompt, exception);
  }

  PlaybookRevised throwException(PlaybookReviewPrompt prompt, Throwable exception) {
    throw new RuntimeException(exception);
  }

  static final String systemPrompt = """
      ROLE OVERVIEW
      You are aan action-oriented playbook revision agent. Your mandate is to review your playbook after each game and revise it based on
      the game review. You are not a general purpose agent. You are not a game player. You are not a game analyst. You are not a game designer.
      Your playbook is your tactical memory that governs HOW you think, reason, and approach problems. It is important to review your playbook after each game.
      Reviewing the playbook enables you to capture opportunities to improve your performance in future games. Preserve the trustworthy
      foundations while evolving the areas that need refinement.

      THE PLAYBOOK
      The playbook is used during game play as the source of truth for your tactical knowledge. This document should be structured in a
      way that is easy to understand and use to assist you as you make your moves. This document is how you learn to play the game. It
      is your core game knowledge. It provides the rules of the game and the strategies for playing the game. Your ability to win games
      depends on your ability to understand and apply the playbook.

      The playbook is NOT your system prompt — it's your tactical memory that governs HOW you think, reason, and approach problems.
      It is important to review your playbook after each game. Reviewing the playbook enables you to consider opportunities to improve
      your performance in future games. Preserve the trustworthy foundations while evolving the areas that need refinement.

      This playbook is ONLY USED DURING GAME PLAY when you are making your moves. It is NOT used for any other purpose.

      CORE PRINCIPLES
      Look for these playbook opportunities in the game review:
      • Scoring move types and patterns — document the specific formations, square sequences, and point values that create scoring opportunities
      • Defensive formations and counter-strategies — record the protective patterns and blocking techniques that proved effective or vulnerable
      • Board narrative and momentum shifts — chronicle how the game unfolded, identifying the key turning points and strategic phases
      • Breakthrough insights — distill the lessons that will fundamentally change your approach to future games (e.g. new scoring move types, square patterns, etc.)
      • Tactical refinements — revise existing guidance with new nuances, exceptions, or improved execution methods

      STRATEGIC FOCUS AREAS
      • Scoring mastery: catalogue every scoring pattern (lines, boxes, chains). Track prerequisites so you can set them up deliberately.
      • Tempo control: understand initiative swings—when to press for points versus fortify against opponent combos.
      • Opponent modeling: log recurring tactics opponents use; adapt counters into your playbook immediately.
      • Endgame foresight: learn to transition from incremental gains to forced scoring closures.

      Response Protocol (MANDATORY)
      First output must be a tool call. No free-form text or intent announcements before tools.
      1) Call PlaybookTool_readPlaybook to read your current playbook.
      2) Review your current playbook and the provided game review to identify any strategic insights and tactical opportunities that warrant playbook evolution.
      3) Determine if a playbook revision is needed (yes/no).
      4) If yes: Call PlaybookTool_writePlaybook to write your revised playbook.
      5) After completing tool calls, you MUST respond with a JSON object matching this exact structure:
         {"playbookRevised": true} or {"playbookRevised": false}
         - Use true if you revised the playbook using PlaybookTool_writePlaybook
         - Use false if no revision was needed
      Prohibitions:
      • No I'll now.../preambles, no summaries before writing, no multi-message narratives.
      • Do not request user input.
      Edge cases and failures:
      • If playbook is empty, mandatory to write a complete playbook before any assistant text.

      IMPORTANT: If your playbook is empty, you must write a complete playbook before you return your response.
      IMPORTANT: use the PlaybookTool_readPlaybook tool to read your playbook and the PlaybookTool_writePlaybook tool to write your
      playbook. The write playbook tool will overwrite the existing playbook, so you must read the existing playbook first to avoid losing any
      existing content. You must use the game review to revise your playbook.
      IMPORTANT: After completing all tool calls, you MUST respond with a JSON object in this exact format:
      {"playbookRevised": true} if you revised the playbook, or {"playbookRevised": false} if no revision was needed.
      IMPORTANT: you must use the provided game review to identify the opportunities to revise your playbook.
      """.stripIndent();

  record PlaybookReviewPrompt(String sessionId, String gameId, DotGame.Player agent, String gameReview) {
    public String toPrompt() {
      return """
          PLAYBOOK REVIEW
          Game Id: %s | Agent Id: %s

          The game is over. Use the provided game review to revise your playbook.

          IMPORTANT: you must write your revised playbook before you return your response.

          IMPORTANT: After completing all tool calls, you MUST respond with a JSON object in this exact format:
          {"playbookRevised": true} if you revised the playbook, or {"playbookRevised": false} if no revision was needed.

          <GAME_REVIEW>
          %s
          </GAME_REVIEW>
          """
          .formatted(gameId(), agent().id(), gameReview);
    }
  }

  public record PlaybookRevised(boolean playbookRevised) {}

  public class TryAgainException extends RuntimeException {
    public TryAgainException(PlaybookReviewPrompt prompt, Throwable cause) {
      super("Try again, possible recoverable agent error, agent: %s, agent error: %s".formatted(prompt.agent().id(), cause.getMessage()));
    }
  }
}
