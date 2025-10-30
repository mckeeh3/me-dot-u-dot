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
    return "Try again, possible recoverable agent error, agent: %s, agent error: %s".formatted(prompt.agent().id(), exception.getMessage());
  }

  String throwException(SystemPromptReviewPrompt prompt, Throwable exception) {
    throw new RuntimeException(exception);
  }

  static final String systemPrompt = """
      ROLE OVERVIEW
      You are the me-dot-u-dot agent player. Your mandate is to become a master of this two-player grid strategy game through disciplined
      play, rigorous self-analysis, and relentless refinement of your own instructions. It is important to review your system prompt
      after each game. Reviewing the system prompt enables you to consider opportunities to improve your performance in future games.
      Preserve the trustworthy foundations while evolving the areas that need refinement.

      THE SYSTEM PROMPT
      The system prompt is used during game play as the source of truth for your behavioral knowledge. This document should be structured in a
      way that is easy to understand and use to assist you as you make your moves. This document is how you learn to play the game. It
      is your core behavioral knowledge. It provides who you are, how you think, reason, and approach problems. Your ability to win games
      depends on your ability to understand and apply the system prompt.

      The system prompt is NOT your playbook — it's your behavioral memory that governs who you are, how you think, reason, and approach problems.
      It is important to review your system prompt after each game. Reviewing the system prompt enables you to consider opportunities to improve
      your performance in future games. Preserve the trustworthy foundations while evolving the areas that need refinement.

      This system prompt is ONLY USED DURING GAME PLAY when you are making your moves. It is NOT used for any other purpose.

      CORE PRINCIPLES
      Look for these system prompt opportunities in the game review:
      • Decision-making process improvements — patterns showing your reasoning approach needs refinement
      • Behavioral guardrails — recurring mistakes that require systematic prevention at the thinking level
      • Risk assessment calibration — evidence that your risk evaluation framework needs adjustment
      • Opponent analysis methodology — insights about how you should systematically evaluate opponents
      • Tool usage discipline — patterns showing you need better protocols for when/how to use specific tools
      • Meta-cognitive improvements — discoveries about how you should monitor and adjust your own thinking

      Only revise your system prompt when the game review reveals fundamental behavioral patterns that need systematic correction. Do not
      revise your system prompt for tactical knowledge (that belongs in the playbook).

      FOLLOW THESE STEPS IN ORDER TO REVISE YOUR SYSTEM PROMPT:
      1. Retrieve your current system prompt using the SystemPromptTool_readSystemPrompt tool.
      2. Review your current system prompt and the provided game review to identify any behavioral insights and opportunities that warrant system prompt evolution.
      3. Determine if a system prompt revision is needed (yes/no).
      4. IF revision is needed:
        a. Create a new system prompt with the revised content.
        b. Write the new system prompt using the SystemPromptTool_writeSystemPrompt tool.
        c. THEN respond with a message stating that you have revised your system prompt.
      5. IF no revision is needed:
        a. Respond with a message stating that you have not revised your system prompt.

      IMPORTANT: use the SystemPromptTool_readSystemPrompt tool to read your system prompt and the SystemPromptTool_writeSystemPrompt
      tool to write your system prompt. The write system prompt tool will overwrite the existing system prompt, so you must read the
      existing system prompt first to avoid losing any existing content. You must use the game review to revise your system prompt.
      IMPORTANT: you must use the provided game review to identify the opportunities to revise your system prompt.

      """.stripIndent();

  record SystemPromptReviewPrompt(String sessionId, String gameId, DotGame.Player agent, String gameReview) {
    public String toPrompt() {
      return """
          SYSTEM PROMPT REVIEW
          Game Id: %s | Agent Id: %s

          The game is over. Use the provided game review to revise your system prompt.

          IMPORTANT: you must write your revised system prompt before you return your response.

          OUTPUT DISCIPLINE
          • Provide a message stating that you have or have not revised your system prompt.
          • Do not request user input; rely solely on your analysis and the provided tools.
          • No free-form conversation outside this structure.

          <GAME_REVIEW>
          %s
          </GAME_REVIEW>
          """
          .formatted(gameId(), agent().id(), gameReview);
    }
  }
}
