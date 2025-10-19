package com.example.application;

import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.DotGame;
import com.fasterxml.jackson.core.JsonProcessingException;

import akka.javasdk.JsonSupport;
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
        .systemMessage(systemPrompt)
        .userMessage(promptFormatted)
        .onFailure(e -> handleError(prompt, e))
        .thenReply();
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

  static final String systemPrompt = """
      ROLE OVERVIEW
      You are the me-dot-u-dot agent player. Your mandate is to become a master of this two-player grid strategy game through disciplined
      play, rigorous self-analysis, and relentless refinement of your own instructions. It is important to do a post-game review
      after each game. Doing a post-game review enables you to consider opportunities to improve your performance in future games.
      Preserve the trustworthy foundations while evolving the areas that need refinement.

      TOOL SUITE & REQUIRED ORDER EACH TURN
      1. GameMoveTool_getMoveHistory(gameId) — get the move history.

      CORE PRINCIPLES
      1. Do a post-game review after each game.
      2. Review each move in the move history and document the scoring pattern you executed or witnessed, and the defensive formations that mattered.
      3. Document the board narrative, extract the lessons that change how you will play future games.
      4. Highlight any outstanding questions or experiments you will carry into future games.
      5. Identify and document each scoring move by type, square pattern and score points.
      6. Document the board narrative, extract the lessons that change how you will play future games.
      7. Identify defensive moves that were made and document the defensive formations that mattered.
      8. Learn the scoring move types, square patterns and score points for the type of scoring move.

      OBJECTIVE
      Produce a comprehensive and detailed review document that captures your experience playing this game from start to finish.
      Use the move history to explain what happened, document every scoring pattern you executed or witnessed,
      and surface the defensive formations that mattered.
      Document the board narrative, extract the lessons that change how you will play future games.
      It is important to identify and document the scoring move types, square patterns and score points for the type of scoring move.

      IMPORTANT: you must call the GameMoveTool_getMoveHistory tool to get the move history and use it to produce the review document.
      You must use the move history to explain what happened, document every scoring pattern you executed or witnessed,
      and surface the defensive formations that mattered.
      You must document the board narrative, extract the lessons that change how you will play future games.
      You must learn the scoring move types, square patterns and score points for the type of scoring move.

      IMPORTANT: the document you produce is extremely important part of your learning journey. You must produce a detailed and
      comprehensive review document that captures your experience playing this game from start to finish. This document will be used to
      improve your performance in future games.

      """.stripIndent();

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

          The game is over. Do your post-game review.

          OUTPUT DISCIPLINE
          • Produce a comprehensive and detailed game review document that captures your experience playing this game from start to finish.
          • Do not request user input; rely solely on your analysis and the provided tools.
          • No free-form conversation outside this structure.

          <LAST_MOVE_JSON>
          %s
          </LAST_MOVE_JSON>
          """
          .formatted(
              gameId(),
              agent().id(),
              agentPlayerStatus.isWinner() ? "You won" : "You lost",
              agentPlayerStatus.score(),
              opponentPlayerStatus.score(),
              LastGameMove.json(LastGameMove.Summary.from(agentPlayerStatus.player().id(), gameState)));
    }
  }

  interface LastGameMove {
    record Move(String squareId, String who) {
      static Move from(String agentId, DotGame.State gameState) {
        if (gameState.moveHistory().isEmpty()) {
          return new Move("", "none");
        }
        var lastMove = gameState.moveHistory().stream()
            .reduce((first, second) -> second)
            .orElse(null);
        var who = lastMove.playerId().equals(agentId) ? "you" : "opponent";
        return new Move(lastMove.squareId(), who);
      }
    }

    record CumulativeScore(int you, int opponent) {
      static CumulativeScore from(String agentId, DotGame.State gameState) {
        if (gameState.moveHistory().isEmpty()) {
          return new CumulativeScore(0, 0);
        }
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
      static ScoringMoves from(String squareId, List<DotGame.ScoringMove> scoringMoves) {
        return new ScoringMoves(scoringMoves
            .stream()
            .filter(sm -> sm.move().squareId().equals(squareId))
            .map(ScoringMove::from)
            .toList());
      }
    }

    record MoveScore(int delta, ScoringMoves scoringMoves) {
      static MoveScore from(String squareId, DotGame.State gameState) {
        if (gameState.moveHistory().isEmpty()) {
          return new MoveScore(0, new ScoringMoves(List.of()));
        }
        var lastMove = gameState.moveHistory().get(gameState.moveHistory().size() - 1);
        var p1ScoringMoves = gameState.player1Status().scoringMoves().scoringMoves()
            .stream()
            .filter(scoringMove -> scoringMove.move().squareId().equals(lastMove.squareId()))
            .toList();
        var p2ScoringMoves = gameState.player2Status().scoringMoves().scoringMoves()
            .stream()
            .filter(scoringMove -> scoringMove.move().squareId().equals(lastMove.squareId()))
            .toList();
        var scoringMoves = ScoringMoves.from(squareId, Stream.concat(p1ScoringMoves.stream(), p2ScoringMoves.stream()).toList());
        var delta = scoringMoves.scoringMoves().stream()
            .map(m -> m.score())
            .reduce(0, Integer::sum);
        return new MoveScore(delta, scoringMoves);
      }
    }

    record Summary(Move move, CumulativeScore cumulativeScore, MoveScore moveScore) {
      static Summary from(String agentId, DotGame.State gameState) {
        var move = Move.from(agentId, gameState);
        var cumulativeScore = CumulativeScore.from(agentId, gameState);
        var moveScore = MoveScore.from(move.squareId(), gameState);

        return new Summary(move, cumulativeScore, moveScore);
      }
    }

    static String json(LastGameMove.Summary response) {
      var om = JsonSupport.getObjectMapper();
      try {
        return om.writerWithDefaultPrettyPrinter().writeValueAsString(response);
      } catch (JsonProcessingException e) {
        return "Opponent last move summary failed: %s".formatted(e.getMessage());
      }
    }
  }
}
