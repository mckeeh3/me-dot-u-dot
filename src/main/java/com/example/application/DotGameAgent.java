package com.example.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.DotGame;
import com.example.domain.Playbook;

import akka.javasdk.JsonSupport;
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.JsonParsingException;
import akka.javasdk.agent.ModelException;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.agent.ModelTimeoutException;
import akka.javasdk.agent.RateLimitException;
import akka.javasdk.agent.ToolCallExecutionException;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;

@ComponentId("dot-game-agent")
public class DotGameAgent extends Agent {
  static final Logger log = LoggerFactory.getLogger(DotGameAgent.class);
  final ComponentClient componentClient;
  final List<Object> functionTools;

  public DotGameAgent(ComponentClient componentClient) {
    this.componentClient = componentClient;
    this.functionTools = List.of(
        new GetGameStateTool(componentClient),
        new GetYourPlaybookTool(componentClient),
        new MakeMoveTool(componentClient),
        new UpdateYourPlaybookTool(componentClient),
        new GetYourSystemPromptTool(componentClient),
        new UpdateYourSystemPromptTool(componentClient),
        new GetGameMoveHistoryTool(componentClient));
  }

  public Effect<String> makeMove(MakeMovePrompt prompt) {
    log.debug("MakeMovePrompt: {}", prompt);

    return effects()
        .model(ModelProvider.fromConfig("ai-agent-model-" + prompt.agentModel))
        .tools(functionTools)
        .systemMessage(systemPrompt(prompt.agentId))
        .userMessage(prompt.toPrompt())
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

  String handleError(MakeMovePrompt prompt, Throwable exception) {
    return switch (exception) {
      case ModelException e -> tryAgain(prompt, e);
      case RateLimitException e -> forfeitMoveDueToError(prompt, e);
      case ModelTimeoutException e -> tryAgain(prompt, e);
      case ToolCallExecutionException e -> tryAgain(prompt, e);
      case JsonParsingException e -> tryAgain(prompt, e);
      case NullPointerException e -> tryAgain(prompt, e);
      default -> forfeitMoveDueToError(prompt, exception);
    };
  }

  String tryAgain(MakeMovePrompt prompt, Throwable exception) {
    return "Try again, possible recoverable agent error, agent: %s, agent error: %s".formatted(prompt.agentName, exception.getMessage());
  }

  String forfeitMoveDueToError(MakeMovePrompt prompt, Throwable exception) {
    log.error("Forfeiting move due to agent error: %s".formatted(exception.getMessage()), exception);

    var message = "Agent: %s, forfeited move due to agent error: %s".formatted(prompt.agentName, exception.getMessage());
    var command = new DotGame.Command.ForfeitMove(prompt.gameId, prompt.agentId, message);

    componentClient
        .forEventSourcedEntity(prompt.gameId)
        .method(DotGameEntity::forfeitMove)
        .invoke(command);

    return "Forfeited move due to agent error: %s".formatted(exception.getMessage());
  }

  String gameStateAsJson(String gameId) {
    var result = componentClient
        .forEventSourcedEntity(gameId)
        .method(DotGameEntity::getState)
        .invoke();

    if (result instanceof DotGame.State) {
      return JsonSupport.encodeToString(GetGameStateTool.CompactGameState.from((DotGame.State) result));
    }

    return "Error getting game state for gameId: %s".formatted(gameId);
  }

  String playbookAsJson(String agentId) {
    var playbook = componentClient
        .forEventSourcedEntity(agentId)
        .method(PlaybookEntity::getState)
        .invoke();

    if (playbook instanceof Playbook.State) {
      return JsonSupport.encodeToString(playbook);
    }

    return "Error getting playbook for agentId: %s".formatted(agentId);
  }

  public record MakeMovePrompt(
      String gameId,
      DotGame.Status status,
      String agentId,
      String agentName,
      String agentModel) {

    public String toPrompt() {
      if (status == DotGame.Status.in_progress) {
        return """
            It's your turn to make a move.
            Your Id is %s.
            Your Name is %s.
            Here's the current game Id: %s.

            Use the get game state tool to get the current game state and use the get your playbook tool to get your playbook.
            Use this information to decide how to make your next move.
            ALWAYS use the make move tool to make your move when it is your turn.
            Optionally, you can use the update playbook tool to update your playbook and the update system prompt tool to update your system prompt.
            """.formatted(agentId, agentName, gameId).stripIndent();
      }

      return """
          The game is over.
          Your Id is %s.
          Your Name is %s.
          Here's the current game Id: %s.

          IMPORTANT: Do your post game review and analysis.
          Use the GetGameMoveHistoryTool to get the move history for the game.
          Use the insights you gather here with your playbook and system prompt updates—this data helps you choose which tactics to memorialise in the playbook and which behavioral adjustments belong in the system prompt.
          You can optionally update your playbook and system prompt via the provided tools to capture your learnings  and experience
          from this game to improve your performance in future games.
          """.formatted(agentId, agentName, gameId).stripIndent();
    }
  }
}
