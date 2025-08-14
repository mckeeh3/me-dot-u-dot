package com.example.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.DotGame;
import com.fasterxml.jackson.core.JsonProcessingException;

import akka.javasdk.JsonSupport;
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
        .userMessage("It's your turn %s. Here's the current game state: %s"
            .formatted(prompt.yourStatus().player().name(), prompt.toPrompt()))
        .thenReply();
  }

  public record MakeMovePrompt(
      String gameId,
      DotGame.Board board,
      DotGame.Status status,
      DotGame.PlayerStatus yourStatus,
      DotGame.PlayerStatus opponentStatus,
      List<DotGame.Move> moveHistory) {

    public String toPrompt() {
      try {
        var json = JsonSupport.getObjectMapper().writeValueAsString(this);

        return switch (status) {
          case DotGame.Status.in_progress -> """
              It's your turn to make a move.

              Here's the current game state:
              %s
              """.formatted(json).stripIndent();
          case DotGame.Status.won_by_player -> {
            var winner = yourStatus.isWinner() ? yourStatus.player().name() : opponentStatus.player().name();
            var yourOutcome = yourStatus.isWinner() ? "won" : "lost";
            yield """
                The game is over. The winner is %s. You %s.

                Here's the final game state:
                %s
                """.formatted(winner, yourOutcome, json).stripIndent();
          }
          case DotGame.Status.draw -> """
              The game is over. It's a draw.

              Here's the final game state:
              %s
              """.formatted(json).stripIndent();
          default -> throw new IllegalStateException("Unexpected game status: " + status);
        };
      } catch (JsonProcessingException e) {
        log.error("Error serializing MakeMovePrompt", e);
        throw new RuntimeException("Error serializing MakeMovePrompt", e);
      }
    }
  }
}
