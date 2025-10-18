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

@Component(id = "agent-player-post-game-review-agent")
public class AgentPlayerPostGameReviewAgent extends Agent {
  static final Logger log = LoggerFactory.getLogger(AgentPlayerPostGameReviewAgent.class);
  final ComponentClient componentClient;
  final GameActionLogger gameLog;
  final List<Object> functionTools;

  public AgentPlayerPostGameReviewAgent(ComponentClient componentClient) {
    this.componentClient = componentClient;
    this.gameLog = new GameActionLogger(componentClient);
    this.functionTools = List.of(
        new GameMoveTool(componentClient));
  }

  public Effect<String> postGameReview(PostGameReviewPrompt prompt) {
    var promptFormatted = prompt.toPrompt(componentClient);

    log.debug("PostGameReviewPrompt: {}", prompt);
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

  String handleError(PostGameReviewPrompt prompt, Throwable exception) {
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

  String tryAgain(PostGameReviewPrompt prompt, Throwable exception) {
    return "Try again, possible recoverable agent error, agent: %s, agent error: %s".formatted(prompt.agent().id(), exception.getMessage());
  }

  String throwException(PostGameReviewPrompt prompt, Throwable exception) {
    throw new RuntimeException(exception);
  }

  record PostGameReviewPrompt(String sessionId, String gameId, DotGame.Player agent) {
    public String toPrompt(ComponentClient componentClient) {
      var gameState = componentClient
          .forEventSourcedEntity(gameId())
          .method(DotGameEntity::getState)
          .invoke();

      var agentId = agent.id();
      var agentPlayerStatus = gameState.player1Status().player().id().equals(agentId)
          ? gameState.player1Status()
          : gameState.player2Status();
      var opponentPlayerStatus = gameState.player1Status().player().id().equals(agent.id())
          ? gameState.player2Status()
          : gameState.player1Status();

      return """
          POST-GAME LEARNING REVIEW
          Game Id: %s | Agent Id: %s | Result: %s | Your Score: %d | Opponent Score: %d

          OBJECTIVE
          Produce a comprehensive and detailed review document that captures your experience playing this game from start to finish.
          Use the move history to explain what happened, document every scoring pattern you executed or witnessed,
          and surface the defensive formations that mattered.
          Document the board narrative, extract the lessons that change how you will play future games.
          It is important to learn the scoring move types, square patterns and score points for the type of scoring move..

          IMPORTANT: you must call the GameMoveTool_getMoveHistory tool to get the move history and use it to produce the review document.
          You must use the move history to explain what happened, document every scoring pattern you executed or witnessed,
          and surface the defensive formations that mattered.
          You must document the board narrative, extract the lessons that change how you will play future games.
          You must learn the scoring move types, square patterns and score points for the type of scoring move.

          OUTPUT DISCIPLINE
          • Produce a comprehensive and detailed review document that captures your experience playing this game from start to finish.
          • Highlight any outstanding questions or experiments you will carry into future games.
          • Do not request user input; rely solely on your analysis and the provided tools.

          <LAST_MOVE_JSON>
          %s
          </LAST_MOVE_JSON>
          """
          .formatted(
              gameId(),
              agent().id(),
              agentPlayerStatus.isWinner() ? "You Won" : "You lost",
              agentPlayerStatus.score(),
              opponentPlayerStatus.score(),
              LastGameMove.json(LastGameMove.Summary.from(agentPlayerStatus.player().id(), gameState)));
    }
  }
}
