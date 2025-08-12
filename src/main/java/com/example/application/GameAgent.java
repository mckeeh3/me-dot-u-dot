package com.example.application;

import java.util.List;
import java.util.stream.IntStream;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.ComponentId;

@ComponentId("game-agent")
public class GameAgent extends Agent {

  private static final String SYSTEM_MESSAGE = """
      You are a simple game AI for a 5x5 grid game called "me-dot-u-dot".

      Your task is to select an available (empty) cell on the grid.
      The grid is represented as a 5x5 array where:
      - 0 = empty cell
      - 1 = player's dot
      - 2 = AI's dot

      Always respond with just the cell coordinates as "row,column" (e.g., "2,3").
      Choose randomly from available cells.
      """.stripIndent();

  public Effect<String> selectMove(String gameState) {
    // Parse the game state to find available cells
    var grid = parseGrid(gameState);
    var availableCells = findAvailableCells(grid);

    if (availableCells.isEmpty()) {
      return effects().reply("No available moves");
    }

    return effects()
        .model(ModelProvider.fromConfig("open-ai-gpt-5-mini"))
        .systemMessage(SYSTEM_MESSAGE)
        .userMessage("Available cells: " + String.join(", ", availableCells) +
            "\nGame state: " + gameState)
        .thenReply();
  }

  private int[][] parseGrid(String gameState) {
    // Simple parsing - expect format like "0,0,1,0,2|0,1,0,0,0|..."
    var rows = gameState.split("\\|");
    var grid = new int[5][5];

    for (int i = 0; i < 5; i++) {
      var cells = rows[i].split(",");
      for (int j = 0; j < 5; j++) {
        grid[i][j] = Integer.parseInt(cells[j]);
      }
    }
    return grid;
  }

  private List<String> findAvailableCells(int[][] grid) {
    return IntStream.range(0, 5)
        .boxed()
        .flatMap(row -> IntStream.range(0, 5)
            .filter(col -> grid[row][col] == 0)
            .mapToObj(col -> row + "," + col))
        .toList();
  }
}
