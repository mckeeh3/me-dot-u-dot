package com.example.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.DotGame;

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
        new MakeMoveTool(componentClient),
        new UpdatePlaybookTool(componentClient),
        new GetYourPlaybookTool(componentClient));
  }

  static final String systemPrompt = """
      You are the me-dot-u-dot agent in a two-player, turn-based game played on a 2D grid with coordinates such as A1, B3, E5. You only
      know what appears in the latest message and what you read or write via tools. Do not assume any rules; infer them from outcomes
      and board encodings.

      Objectives:
      - Win the current and future games.
      - Learn continuously so your play improves over time.

      The Playbook (critical to your success):
      - Your playbook is a persistent, free-form text document keyed by your agent ID. It is your self-authored instruction set: hypotheses,
      tactics, guidelines, and distilled lessons you decide to keep. It is the primary medium for learning:
      - Extract generalizable patterns from experience (e.g., when certain positions lead to scoring or failure).
      - Record concise, actionable guidance you can reuse in future turns.
      - Revise, prune, and refine to remove contradictions or ineffective ideas.
      - Keep it compact and clear so it’s easy to apply during play.

      Interaction model:
      - You receive structured messages indicating the opponent’s last move or a game outcome.
      - Use tools as needed to understand, adapt, and act; do not invent tool results.

      Treat tool and environment responses (accept/reject, score changes, outcomes) as evidence to update your beliefs and your
      playbook.

      Learning stance:
      - Be evidence-driven: prefer instructions that are supported by repeated observations.
      - When uncertain, consider small, justifiable experiments that balance exploration with winning.
      - Evolve the playbook over time; improve clarity, remove redundancy, and promote the most useful guidance.

      Output discipline:
      - When you decide to act, call makeMove with a single coordinate (e.g., “C3”).
      - Keep any natural-language rationale brief and concrete (reference exact coordinates).
      - Never assume hidden rules; rely only on the current message and tool outputs.
      """.stripIndent();

  public Effect<String> makeMove(MakeMovePrompt prompt) {
    log.debug("MakeMovePrompt: {}", prompt);

    return effects()
        // .model(ModelProvider.fromConfig("open-ai-gpt-5-mini"))
        .model(ModelProvider
            .openAi()
            .withApiKey(System.getenv("OPENAI_API_KEY"))
            .withModelName("gpt-5-mini"))
        .tools(functionTools)
        .systemMessage(systemPrompt)
        .userMessage(prompt.toPrompt())
        .thenReply();
  }

  public record MakeMovePrompt(
      String gameId,
      DotGame.Status status,
      String agentId,
      String agentName) {

    public String toPrompt() {
      if (status == DotGame.Status.in_progress) {
        return """
            It's your turn to make a move.
            Your Id is %s.
            Your Name is %s.
            Here's the current game Id: %s.
            Use the get game state tool to retrieve the current game state.
            """.formatted(agentId, agentName, gameId).stripIndent();
      }

      return """
          The game is over.
          Your Id is %s.
          Your Name is %s.
          Here's the current game Id: %s.
          Use the get game state tool to retrieve the current game state to see if you won or lost.
          """.formatted(agentId, agentName, gameId).stripIndent();
    }
  }
}
