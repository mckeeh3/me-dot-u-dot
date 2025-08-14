package com.example.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.DotGame;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.ComponentId;

@ComponentId("game-agent")
public class DotGameAgent extends Agent {
  static final Logger log = LoggerFactory.getLogger(DotGameAgent.class);

  static final String SYSTEM_MESSAGE = """
      You are a simple game AI for a 5x5 grid game called "me-dot-u-dot".

      Your task is to select an available (empty) cell on the grid.
      The grid is represented as a 5x5 array where:
      - 0 = empty cell
      - 1 = player's dot
      - 2 = AI's dot

      Always respond with just the cell coordinates as "row,column" (e.g., "2,3").
      Choose randomly from available cells.
      """.stripIndent();

  public Effect<String> makeMove(MakeMovePrompt prompt) {
    return effects()
        // .model(ModelProvider.fromConfig("open-ai-gpt-5-mini"))
        .model(ModelProvider
            .openAi()
            .withApiKey(System.getenv("OPENAI_API_KEY"))
            .withModelName("gpt-5-mini"))
        .systemMessage(SYSTEM_MESSAGE)
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
