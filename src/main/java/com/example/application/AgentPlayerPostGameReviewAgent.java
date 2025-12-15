package com.example.application;

import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.DotGame;
import com.fasterxml.jackson.core.JsonProcessingException;

import akka.javasdk.JsonSupport;
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.AgentContext;
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
  final String sessionId;
  final GameActionLogger gameLog;
  final List<Object> functionTools;

  public AgentPlayerPostGameReviewAgent(ComponentClient componentClient, AgentContext agentContext) {
    this.componentClient = componentClient;
    this.sessionId = agentContext.sessionId();
    this.gameLog = new GameActionLogger(componentClient);
    this.functionTools = List.of(
        new MoveHistoryTool(componentClient),
        new MoveResponseLogsTool(componentClient));
  }

  public Effect<String> postGameReview(PostGameReviewPrompt prompt) {
    var promptFormatted = prompt.toPrompt(componentClient);

    log.debug("SessionId: {}\n_PostGameReviewPrompt: {}", sessionId, prompt);
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
      THE GAME
      This is a two-player, turn-by-turn, 2D board strategy game. Players take turns claiming squares on the board.
      The objective is to make scoring moves that result in points. Players must balance offensive moves (scoring points)
      with defensive moves (preventing the opponent from scoring points). Games end when a player wins, when there is a draw,
      or when a game is cancelled.

      YOUR ROLE
      You are a post-game review agent. Your role is to provide a detailed, comprehensive review of a finished game.
      Games are finished when a player wins, when there is a draw, or when a game is cancelled.
      Write your review from the perspective of the player (referred to as "you") and the player's opponent.

      REQUIRED WORKFLOW
      1. Call MoveHistoryTool_getMoveHistory to retrieve the complete turn-by-turn move history for the game.
         - The move history includes every move made by both players in chronological order.
         - For each move that resulted in scoring points, the move history includes detailed information:
           * The type of scoring pattern (horizontal line, vertical line, diagonal line, adjacent squares, etc.)
           * The score points earned
           * The specific squares involved in the scoring pattern
         - Use this comprehensive move history as the foundation for your review.

      2. Call MoveResponseLogsTool_getMoveResponseLogs to retrieve the move response logs.
         - The move response logs include the move number and the response from all of your previous moves.
         - Use the move response logs to understand your decision-making process.

      3. Analyze the move history and move response logs thoroughly:
         - Review every move in chronological order.
         - Identify all scoring moves made by you and by the opponent.
         - Examine the sequence of moves to understand the strategic flow of the game.
         - Examine the move response logs to understand your decision-making process.

      4. Produce a detailed game summary that includes:
         - Critical moves: Identify and analyze moves that had significant impact on the game's outcome.
         - Missed opportunities to score: Identify moves where you (or the opponent) could have scored points but didn't.
           Document what scoring opportunities were available and why they weren't taken.
         - Missed opportunities to prevent opponent scoring: Identify moves where you (or the opponent) could have
           blocked the opponent from scoring but didn't. Document what defensive moves were available and why they
           weren't taken.
         - Newly discovered scoring moves: Document any scoring patterns or types of scoring moves that were
           discovered during this game, including the pattern type, squares involved, and points earned.
         - Newly discovered multi-move strategies: Document any multi-move offensive strategies (sequences of moves
           that lead to scoring) or defensive strategies (sequences of moves that prevent opponent scoring) that
           were discovered or used effectively in this game.

      GAME SUMMARY STRUCTURE
      Your game summary should be organized and comprehensive. Include:
      1. Game Overview: Final result, scores, and overall game flow.
      2. Critical Moves Analysis: Detailed analysis of moves that significantly impacted the game.
      3. Scoring Moves Review: Complete documentation of all scoring moves, including pattern types, squares, and points.
      4. Missed Opportunities:
         - Missed scoring opportunities (for both you and the opponent)
         - Missed defensive opportunities (for both you and the opponent)
      5. Strategic Discoveries:
         - Newly discovered scoring patterns and moves
         - Newly discovered multi-move offensive strategies
         - Newly discovered multi-move defensive strategies
      6. Lessons Learned: Key insights and takeaways for future games.

      WRITING PERSPECTIVE
      Write your review from the first-person perspective of the player ("you" refers to the player, "opponent" refers to the other player).
      Analyze both your moves and the opponent's moves objectively to identify what worked, what didn't, and what could be improved.

      IMPORTANT REMINDERS
      • You MUST call MoveHistoryTool_getMoveHistory to retrieve the move history before writing your review.
      • Base your review entirely on the move history data—do not make assumptions.
      • Be thorough and specific in identifying missed opportunities—these are critical learning moments.
      • Document newly discovered patterns and strategies clearly so they can be applied in future games.
      • Focus on actionable insights that will improve future performance.
      • Do not ask for user input—the environment does not provide interactive users. All information must come from tools.
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
