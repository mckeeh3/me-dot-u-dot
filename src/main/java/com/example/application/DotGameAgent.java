package com.example.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.DotGame;
import com.example.domain.DotGame.PlayerStatus;
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

@Component(id = "dot-game-agent")
public class DotGameAgent extends Agent {
  static final Logger log = LoggerFactory.getLogger(DotGameAgent.class);
  final ComponentClient componentClient;
  final GameActionLogger gameLog;
  final List<Object> functionTools;

  public DotGameAgent(ComponentClient componentClient) {
    this.componentClient = componentClient;
    this.gameLog = new GameActionLogger(componentClient);
    this.functionTools = List.of(
        new GameStateTool(componentClient),
        new GameMoveTool(componentClient),
        new PlaybookTool(componentClient),
        new SystemPromptTool(componentClient));
  }

  public Effect<String> makeMove(MakeMovePrompt prompt) {
    var promptFormatted = prompt.toPrompt(componentClient);

    log.debug("MakeMovePrompt: {}", prompt);
    gameLog.logModelPrompt(prompt.gameId, prompt.playerStatus().player().id(), promptFormatted);

    return effects()
        .model(ModelProvider.fromConfig("ai-agent-model-" + prompt.playerStatus().player().model()))
        .tools(functionTools)
        .systemMessage(systemPrompt(prompt.playerStatus().player().id()))
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
    return "Try again, possible recoverable agent error, agent: %s, agent error: %s".formatted(prompt.playerStatus().player().id(), exception.getMessage());
  }

  String forfeitMoveDueToError(MakeMovePrompt prompt, Throwable exception) {
    log.error("Forfeiting move due to agent error: %s".formatted(exception.getMessage()), exception);

    var message = "Agent: %s, forfeited move due to agent error: %s".formatted(prompt.playerStatus().player().id(), exception.getMessage());
    var command = new DotGame.Command.ForfeitMove(prompt.gameId, prompt.playerStatus().player().id(), message);

    componentClient
        .forEventSourcedEntity(prompt.gameId)
        .method(DotGameEntity::forfeitMove)
        .invoke(command);

    return "Forfeited move due to agent error: %s".formatted(exception.getMessage());
  }

  public record MakeMovePrompt(
      String gameId,
      DotGame.Status status,
      PlayerStatus playerStatus,
      int opponentScore) {

    public String toPrompt(ComponentClient componentClient) {
      var gameState = componentClient
          .forEventSourcedEntity(gameId())
          .method(DotGameEntity::getState)
          .invoke();

      if (status() == DotGame.Status.in_progress) {
        return """
            TURN BRIEFING — YOUR MOVE
            Game Id: %s | Agent Id: %s | Game Status: %s | Your Score: %d | Opponent Score: %d

            CORE REMINDERS
            • You must read your latest playbook and the current game state before planning.
            • Envision the board three moves ahead: identify scoring chances, opponent threats, and tempo shifts.
            • Your response must include a single GameMoveTool_makeMove call plus concise strategic commentary.

            REQUIRED FLOW FOR THIS TURN
            1. PlaybookTool_readPlaybook to refresh applicable tactics.
            2. GameStateTool_getGameState to inspect the precise board snapshot.
            3. Evaluate candidate moves: projected score, defensive coverage, future hooks.
            4. Choose the move that best advances your long-term scoring plan while guarding against immediate counter play.
            5. IMPORTANT: Call GameMoveTool_makeMove with the chosen square to make your move.
            6. Immediately articulate: current board summary, reason for the move, key risks, lessons for your playbook.

            LEARNING PROMPT
            • Note any new scoring formations, opponent habits, or mistakes worth memorializing after the turn.
            • If you discover rules or counter strategies you do not yet master, flag them for post-game study.
            • Capture concise hypotheses you will test in upcoming moves.

            OUTPUT RULES
            • Call GameMoveTool_makeMove exactly once.
            • Follow with a short strategic reflection covering state, intent, and insights.
            • Do not ask for user input; rely solely on tools and your memories.
            • No free-form conversation outside this structure.

            <OPPONENTS_LAST_MOVE_JSON>
            %s
            </OPPONENTS_LAST_MOVE_JSON>
            """
            .formatted(
                gameId(),
                playerStatus().player().id(),
                status().name(),
                playerStatus().score(),
                opponentScore(),
                json(OpponentLastMove.Summary.from(playerStatus().player().id(), gameState)))
            .stripIndent();
      }

      return """
          POST-GAME LEARNING REVIEW
          Game Id: %s | Agent Id: %s | Result: %s | Your Score: %d | Opponent Score: %d

          OBJECTIVE
          Transform this game into durable expertise. Document the board narrative, extract the lessons that change how you will play future games, and capture the updates that belong in your playbook and system prompt.
          It is important to learn the scoring move types and how to score them.
          When you observe new scoring moves, you should update your playbook to capture the scoring move types and how to score them.

          KNOWLEDGE EXTRACTION
          1. GameMoveTool_getMoveHistory — replay every turn, capturing momentum shifts, scoring bursts, blunders, and tempo swings.
          2. Build a structured review that covers opening patterns, mid-game fights, end-game closures, and any rule interactions that surfaced.
          3. Distill the takeaways into three buckets:
             • Repeatable formations, counter plays, or timing cues to keep practicing
             • Errors (yours or the opponent’s) plus the prevention/exploitation plan
             • Newly confirmed scoring patterns, rule nuances, or hypotheses to test next match

          PLAYBOOK CONSOLIDATION
          1. PlaybookTool_readPlaybook — inspect existing guidance for overlap or gaps.
          2. PlaybookTool_writePlaybook — record the lessons above as structured entries (Situation → Pattern → Move → Outcome → Guideline) so they are ready before your next game.

          SYSTEM PROMPT ALIGNMENT
          1. SystemPromptTool_readSystemPrompt — ensure your behavioral charter reflects today’s insights.
          2. SystemPromptTool_writeSystemPrompt — update only when the core decision framework needs to change to apply what you just learned.

          OUTPUT DISCIPLINE
          • Produce a comprehensive recap (result + storyline) plus a bulletized knowledge base drawn from your analysis.
          • List the playbook and system prompt updates you executed or plan to execute, referencing the exact tool actions by name.
          • Highlight any outstanding questions or experiments you will carry into the next game.
          • Do not request user input; rely solely on your analysis and the provided tools.

          <OPPONENTS_LAST_MOVE_JSON>
          %s
          </OPPONENTS_LAST_MOVE_JSON>
          """
          .formatted(
              gameId(),
              playerStatus().player().id(),
              playerStatus().isWinner() ? "You Won" : "You lost",
              playerStatus().score(),
              opponentScore(),
              json(OpponentLastMove.Summary.from(playerStatus().player().id(), gameState)))
          .stripIndent();
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
  }
}
