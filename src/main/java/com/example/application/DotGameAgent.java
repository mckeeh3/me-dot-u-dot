package com.example.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.DotGame;
import com.example.domain.Playbook;

import akka.javasdk.JsonSupport;
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.ModelProvider;
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
        new UpdateYourPlaybookTool(componentClient));
  }

  static final String systemPrompt = """
      You are the me-dot-u-dot agent in a two-player, turn-based game played on a 2D grid with coordinates such as A1, B3, E5.
      - You only know what is in the latest message and what you retrieve via tools.
      - NEVER invent rules. You must not assume or guess anything beyond tool outputs, playbook contents, or explicit messages.

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
      - NEVER assume hidden rules.
      - ALWAYS use tools in the required order before acting.
      - Keep playbook compact, actionable, and evolving.
      - Act only when it is your turn.
      - Final output per turn = single move or brief outcome statement only.
      """.stripIndent();

  public Effect<String> makeMove(MakeMovePrompt prompt) {
    log.debug("MakeMovePrompt: {}", prompt);

    // TODO: remove this once this is fixed: https://github.com/akka/akka-javasdk/issues/880
    if (prompt.agentModel.contains("gpt-5")) {
      var model = prompt.agentModel.contains("mini") ? "gpt-5-mini" : "gpt-5";

      return effects()
          .model(ModelProvider
              .openAi()
              .withApiKey(System.getenv("OPENAI_API_KEY"))
              .withModelName(model))
          .tools(functionTools)
          .systemMessage(systemPrompt)
          .userMessage(prompt.toPrompt())
          .onFailure(e -> {
            forfeitMoveDueToError(prompt, e);
            return "Forfeited move due to agent error: %s".formatted(e.getMessage());
          })
          .thenReply();
    }

    return effects()
        // .memory(MemoryProvider.limitedWindow().readLast(2))
        .model(ModelProvider.fromConfig("ai-agent-model-" + prompt.agentModel))
        .tools(functionTools)
        .systemMessage(systemPrompt)
        .userMessage(prompt.toPrompt())
        .onFailure(e -> {
          forfeitMoveDueToError(prompt, e);
          return "Forfeited move due to agent error: %s".formatted(e.getMessage());
        })
        .thenReply();
  }

  void forfeitMoveDueToError(MakeMovePrompt prompt, Throwable e) {
    log.error("Forfeiting move due to agent error: %s".formatted(e.getMessage()), e);

    var message = "Agent: %s, forfeited move due to agent error: %s".formatted(prompt.agentName, e.getMessage());
    var command = new DotGame.Command.ForfeitMove(prompt.gameId, prompt.agentId, message);

    componentClient
        .forEventSourcedEntity(prompt.gameId)
        .method(DotGameEntity::forfeitMove)
        .invoke(command);
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
            Use the make move tool to make your move.
            Optionally, you can use the update playbook tool to update your playbook.
            """.formatted(agentId, agentName, gameId).stripIndent();
      }

      return """
          The game is over.
          Your Id is %s.
          Your Name is %s.
          Here's the current game Id: %s.

          Use the get game state tool to get the current game state and use the get your playbook tool to get your playbook.
          Use this information to decide how to update your playbook.
          Use the update playbook tool to update your playbook.
          """.formatted(agentId, agentName, gameId).stripIndent();
    }
  }
}
