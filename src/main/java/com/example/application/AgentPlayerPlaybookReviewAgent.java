package com.example.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.DotGame;

import akka.javasdk.agent.Agent;
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
  final GameActionLogger gameLog;
  final List<Object> functionTools;

  public AgentPlayerPlaybookReviewAgent(ComponentClient componentClient) {
    this.componentClient = componentClient;
    this.gameLog = new GameActionLogger(componentClient);
    this.functionTools = List.of(
        new PlaybookTool(componentClient));
  }

  public Effect<String> playbookReview(PlaybookReviewPrompt prompt) {
    var promptFormatted = prompt.toPrompt();

    log.debug("PlaybookReviewPrompt: {}", prompt);
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

  String handleError(PlaybookReviewPrompt prompt, Throwable exception) {
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

  String tryAgain(PlaybookReviewPrompt prompt, Throwable exception) {
    return "Try again, possible recoverable agent error, agent: %s, agent error: %s".formatted(prompt.agent().id(), exception.getMessage());
  }

  String throwException(PlaybookReviewPrompt prompt, Throwable exception) {
    throw new RuntimeException(exception);
  }

  record PlaybookReviewPrompt(String sessionId, String gameId, DotGame.Player agent, String gameReview) {
    public String toPrompt() {
      return """
          PLAYBOOK REVIEW
          Game Id: %s | Agent Id: %s

          OBJECTIVE
          Analyze the provided game review to identify strategic insights and tactical opportunities that warrant playbook evolution.

          Your mission: Examine the game review for evidence of strategic insights and tactical opportunities that warrant playbook evolution.

          The playbook is NOT your system prompt — it's your tactical memory that governs HOW you think, reason, and approach problems.
          It is important to review your playbook after each game. Reviewing the playbook enables you to consider opportunities to improve
          your performance in future games. Preserve the trustworthy foundations while evolving the areas that need refinement.

          Look for these playbook opportunities in the game review:
          • Scoring move types and patterns — document the specific formations, square sequences, and point values that create scoring opportunities
          • Defensive formations and counter-strategies — record the protective patterns and blocking techniques that proved effective or vulnerable
          • Board narrative and momentum shifts — chronicle how the game unfolded, identifying the key turning points and strategic phases
          • Breakthrough insights — distill the lessons that will fundamentally change your approach to future games (e.g. new scoring move types, square patterns, etc.)
          • Tactical refinements — revise existing guidance with new nuances, exceptions, or improved execution methods

          Make your playbook a living document that grows stronger with each game, transforming raw experience into refined expertise.

          IMPORTANT: use the PlaybookTool_readPlaybook tool to read your playbook and the PlaybookTool_writePlaybook tool to write your
          playbook. The write playbook tool will overwrite the existing playbook, so you must read the existing playbook first to avoid losing any
          existing content. You must use the game review to revise your playbook.
          IMPORTANT: you must use the provided game review to identify the opportunities to revise your playbook.
          IMPORTANT: you must write your revised playbook before you return your response.

          <GAME_REVIEW>
          %s
          </GAME_REVIEW>
          """
          .formatted(gameId(), agent().id(), gameReview);
    }
  }
}
