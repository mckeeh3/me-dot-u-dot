package com.example.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.DotGame;

import akka.javasdk.client.ComponentClient;
import akka.javasdk.agent.Agent;
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
  final GameActionLogger gameLog;
  final List<Object> functionTools;

  public AgentPlayerSystemPromptReviewAgent(ComponentClient componentClient) {
    this.componentClient = componentClient;
    this.gameLog = new GameActionLogger(componentClient);
    this.functionTools = List.of(
        new SystemPromptTool(componentClient));
  }

  public Effect<String> systemPromptReview(SystemPromptReviewPrompt prompt) {
    var promptFormatted = prompt.toPrompt();

    log.debug("SystemPromptReviewPrompt: {}", prompt);
    gameLog.logModelPrompt(prompt.gameId, prompt.agent().id(), promptFormatted);

    return effects()
        .model(ModelProvider.fromConfig("ai-agent-model-" + prompt.agent().model()))
        .tools(functionTools)
        .systemMessage(systemPrompt(prompt.agent().id()))
        .userMessage(promptFormatted)
        .onFailure(e -> handleError(prompt, e))
        .thenReply();
  }

  String systemPrompt(String agentId) {
    var result = componentClient
        .forEventSourcedEntity(agentId)
        .method(AgentRoleEntity::getState)
        .invoke();

    return result.systemPrompt();
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

  record SystemPromptReviewPrompt(String sessionId, String gameId, DotGame.Player agent, String gameReview) {
    public String toPrompt() {
      return """
          SYSTEM PROMPT REVIEW
          Game Id: %s | Agent Id: %s

          OBJECTIVE
          Analyze the provided game review to identify behavioral patterns and decision-making insights that warrant system prompt evolution.

          Your mission: Examine the game review for evidence of systematic behavioral issues, decision-making flaws, or breakthrough
          approaches that should be encoded into your core operational charter.

          The system prompt is NOT your tactical playbook — it's your fundamental behavioral framework that governs HOW you think, reason,
          and approach problems. It is important to review your system prompt after each game. Reviewing the system prompt enables you to
          consider opportunities to improve your performance in future games. Preserve the trustworthy foundations while evolving the areas
          that need refinement.

          Look for these system prompt opportunities in the game review:
          • Decision-making process improvements — patterns showing your reasoning approach needs refinement
          • Behavioral guardrails — recurring mistakes that require systematic prevention at the thinking level
          • Risk assessment calibration — evidence that your risk evaluation framework needs adjustment
          • Opponent analysis methodology — insights about how you should systematically evaluate opponents
          • Tool usage discipline — patterns showing you need better protocols for when/how to use specific tools
          • Meta-cognitive improvements — discoveries about how you should monitor and adjust your own thinking

          Only revise your system prompt when the game review reveals fundamental behavioral patterns that need systematic correction. Do not
          revise your system prompt for tactical knowledge (that belongs in the playbook).

          IMPORTANT: use the SystemPromptTool_readSystemPrompt tool to read your system prompt and the SystemPromptTool_writeSystemPrompt
          tool to write your system prompt. The write system prompt tool will overwrite the existing system prompt, so you must read the
          existing system prompt first to avoid losing any existing content. You must use the game review to revise your system prompt.
          IMPORTANT: you must use the provided game review to identify the opportunities to revise your system prompt.
          IMPORTANT: you must write your revised system prompt before you return your response.

          <GAME_REVIEW>
          %s
          </GAME_REVIEW>
          """
          .formatted(gameId(), agent().id(), gameReview);
    }
  }
}
