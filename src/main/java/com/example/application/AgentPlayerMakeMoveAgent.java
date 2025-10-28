package com.example.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.DotGame;
import com.fasterxml.jackson.core.JsonProcessingException;

import akka.javasdk.client.ComponentClient;
import akka.javasdk.JsonSupport;
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.JsonParsingException;
import akka.javasdk.agent.ModelException;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.agent.ModelTimeoutException;
import akka.javasdk.agent.RateLimitException;
import akka.javasdk.agent.ToolCallExecutionException;
import akka.javasdk.annotations.Component;

@Component(id = "agent-player-make-move-agent")
public class AgentPlayerMakeMoveAgent extends Agent {
  static final Logger log = LoggerFactory.getLogger(AgentPlayerMakeMoveAgent.class);
  final ComponentClient componentClient;
  final GameActionLogger gameLog;
  final List<Object> functionTools;

  public AgentPlayerMakeMoveAgent(ComponentClient componentClient) {
    this.componentClient = componentClient;
    this.gameLog = new GameActionLogger(componentClient);
    this.functionTools = List.of(
        new GameStateTool(componentClient),
        new GameMoveTool(componentClient));
  }

  public Effect<String> makeMove(MakeMovePrompt prompt) {
    var promptFormatted = prompt.toPrompt(componentClient);

    log.debug("MakeMovePrompt: {}", prompt);
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
    return "Try again, possible recoverable agent error, agent: %s, agent error: %s".formatted(prompt.agent().id(), exception.getMessage());
  }

  String forfeitMoveDueToError(MakeMovePrompt prompt, Throwable exception) {
    log.error("Forfeiting move due to agent error: %s".formatted(exception.getMessage()), exception);

    var message = "Agent: %s, forfeited move due to agent error: %s".formatted(prompt.agent().id(), exception.getMessage());
    var command = new DotGame.Command.ForfeitMove(prompt.gameId, prompt.agent().id(), message);

    componentClient
        .forEventSourcedEntity(prompt.gameId)
        .method(DotGameEntity::forfeitMove)
        .invoke(command);

    return "Forfeited move due to agent error: %s".formatted(exception.getMessage());
  }

  record MakeMovePrompt(String sessionId, String gameId, DotGame.Player agent) {
    public String toPrompt(ComponentClient componentClient) {
      var gameState = componentClient
          .forEventSourcedEntity(gameId())
          .method(DotGameEntity::getState)
          .invoke();

      var agentPlayerStatus = gameState.currentPlayerStatus().get();
      var opponentPlayerStatus = gameState.player1Status().player().id().equals(agent.id())
          ? gameState.player2Status()
          : gameState.player1Status();

      return """
          TURN BRIEFING — YOUR MOVE
          Game Id: %s | Agent Id: %s | Game Status: %s | Your Score: %d | Opponent Score: %d

          LEARNING OPPORTUNITIES
          • Note any new scoring formations, opponent habits, or mistakes worth memorializing after the turn.
          • If you discover rules or counter strategies you do not yet master, flag them for post-game study.
          • Capture concise hypotheses you will test in upcoming moves.

          OUTPUT RULES
          • Call GameMoveTool_makeMove exactly once.
          • Follow with a detailed strategic reflection covering state, intent, and insights.
          • Do not ask for user input; rely solely on tools and your memories.
          • No free-form conversation outside this structure.

          <OPPONENTS_LAST_MOVE_JSON>
          %s
          </OPPONENTS_LAST_MOVE_JSON>
          """
          .formatted(
              gameId(),
              agentPlayerStatus.player().id(),
              gameState.status().name(),
              agentPlayerStatus.score(),
              opponentPlayerStatus.score(),
              OpponentLastMove.json(OpponentLastMove.Summary.from(agentPlayerStatus.player().id(), gameState)))
          .stripIndent();
    }
  }

  interface OpponentLastMove {
    record Move(String squareId) {
      static Move from(String agentId, DotGame.State gameState) {
        var squareId = gameState.moveHistory().stream()
            .filter(move -> !move.playerId().equals(agentId))
            .reduce((first, second) -> second)
            .map(DotGame.Move::squareId)
            .orElse("");
        return new Move(squareId);
      }
    }

    record CumulativeScore(int you, int opponent) {
      static CumulativeScore from(String agentId, DotGame.State gameState) {
        var player1Id = gameState.player1Status().player().id();
        var p1Score = gameState.player1Status().score();
        var p2Score = gameState.player2Status().score();
        var you = agentId.equals(player1Id) ? p1Score : p2Score;
        var opponent = agentId.equals(player1Id) ? p2Score : p1Score;

        return new CumulativeScore(you, opponent);
      }
    }

    record ScoringMove(String moveSquareId, String type, int score, List<String> scoringSquareIds) {
      static ScoringMove from(DotGame.ScoringMove scoringMove) {
        var type = switch (scoringMove.type()) {
          case horizontal -> "horizontal line";
          case vertical -> "vertical line";
          case diagonal -> "diagonal line";
          case adjacent -> "multiple adjacent squares";
          case topToBottom -> "connected squares from top edge to bottom edge";
          case leftToRight -> "connected squares from left edge to right edge";
        };
        return new ScoringMove(scoringMove.move().squareId(), type, scoringMove.score(), scoringMove.scoringSquares());
      }
    }

    record ScoringMoves(List<ScoringMove> scoringMoves) {
      static ScoringMoves from(String squareId, DotGame.ScoringMoves scoringMoves) {
        return new ScoringMoves(scoringMoves.scoringMoves()
            .stream()
            .filter(sm -> sm.move().squareId().equals(squareId))
            .map(ScoringMove::from)
            .toList());
      }
    }

    record MoveScore(int delta, ScoringMoves scoringMoves) {
      static MoveScore from(String agentId, String squareId, DotGame.State gameState) {
        var scoringMoves = !agentId.equals(gameState.player1Status().player().id())
            ? gameState.player1Status().scoringMoves()
            : gameState.player2Status().scoringMoves();
        var delta = scoringMoves.scoringMoves()
            .stream()
            .filter(m -> m.move().squareId().equals(squareId))
            .map(m -> m.score())
            .reduce(0, Integer::sum);

        return new MoveScore(delta, ScoringMoves.from(squareId, scoringMoves));
      }
    }

    record Summary(Move move, CumulativeScore cumulativeScore, MoveScore moveScore) {
      static Summary from(String agentId, DotGame.State gameState) {
        var move = Move.from(agentId, gameState);
        var cumulativeScore = CumulativeScore.from(agentId, gameState);
        var moveScore = MoveScore.from(agentId, move.squareId(), gameState);

        return new Summary(move, cumulativeScore, moveScore);
      }
    }

    static String json(OpponentLastMove.Summary response) {
      var om = JsonSupport.getObjectMapper();
      try {
        return om.writerWithDefaultPrettyPrinter().writeValueAsString(response);
      } catch (JsonProcessingException e) {
        return "Opponent last move summary failed: %s".formatted(e.getMessage());
      }
    }
  }
}
