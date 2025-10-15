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

    public String oldToPrompt() {
      if (status == DotGame.Status.in_progress) {
        return """
            It's your turn to make a move.
            Agent Id: %s.
            Game Id: %s.
            Game Status: %s.

            IMPORTANT: It's your turn to make a move in the game.

            Required Actions (in order):
            1. Use PlaybookTool_readYourPlaybook to review your tactical knowledge and strategic guidelines
            2. Use GameStateTool_getGameState to analyze the current board position, scores, and available moves
            3. Analyze the game state using your playbook insights to identify the best move
            4. ALWAYS use GameMoveTool_makeMove to execute your chosen move (this is mandatory)

            Decision-Making Process:
            • Evaluate board position: look for scoring opportunities, defensive needs, strategic positioning
            • Apply playbook tactics: use your learned patterns, opening sequences, and strategic principles
            • Consider opponent behavior: anticipate their likely responses and counter-strategies
            • Choose optimal square: select the move that maximizes your advantage or minimizes opponent opportunities

            Optional Mid-Game Learning:
            - Use PlaybookTool_writeYourPlaybook if you discover new tactical insights during play
            - Use SystemPromptTool_writeYourSystemPrompt if you need to adjust your decision-making approach

            Remember: You must make a move using GameMoveTool_makeMove - this is not optional.
            """.formatted(playerStatus.player().id(), gameId, status)
            .stripIndent();
      }

      return """
          The game is over, you %s.
          Agent Id: %s.
          Game Id: %s.
          Game Status: %s.

          IMPORTANT: Conduct your post-game review and learning process. Your crucial learning resource to improve your performance
          in future games is you playbook.  After a game, you should update your playbook with new insights, successful moves, or
          failure modes to avoid.

          Required Actions:
          1. Use GameMoveTool_getMoveHistory to analyze the complete game sequence and each player's moves and scoring moves
          2. Evaluate your decision-making: identify successful moves, missed opportunities, and strategic errors
          3. Assess opponent patterns and effective counter-strategies you discovered

          Optional Learning Updates:
          - Use PlaybookTool_readYourPlaybook to review your tactical knowledge and strategic guidelines
          - Use PlaybookTool_writeYourPlaybook to capture tactical insights, winning patterns, or strategic corrections
          - Use SystemPromptTool_readYourSystemPrompt to review your core decision-making approach or behavioral tendencies
          - Use SystemPromptTool_writeYourSystemPrompt to adjust your core decision-making approach or behavioral tendencies

          Focus Areas for Updates:
          • Playbook: Specific tactics, opening sequences, endgame strategies, pattern recognition
          • System Prompt: Decision-making philosophy, risk assessment, opponent analysis approach, general behavioral adjustments

          This post-game analysis is crucial for continuous improvement and better performance in future games.
          """.formatted(playerStatus.isWinner() ? "won" : "lost", playerStatus.player().id(), gameId, status)
          .stripIndent();
    }

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
          Convert this match into enduring expertise by extracting actionable lessons and upgrading your playbook and system prompt.
          It is important to learn the scoring move types and how to score them.
          When you observe new scoring moves, you should update your playbook to capture the scoring move types and how to score them.

          REQUIRED GAME MOVE ANALYSIS
          1. GameMoveTool_getMoveHistory — replay every turn, noting scoring bursts, mistakes, and pivotal tempo swings.
          2. Summarize the storyline: opening patterns, mid-game fights, end-game closures.
          3. Identify three categories:
             • Winning formations or counter plays you want to repeat
             • Errors (your own or opponent’s) and how to prevent/exploit them
             • New rules, scoring patterns, or timing nuances confirmed in this match

          REQUIRED PLAYBOOK REVIEW AND OPTIONAL UPDATE
          1. PlaybookTool_readPlaybook — review your playbook to see if there are any new insights or patterns you want to add.
          2. PlaybookTool_writePlaybook — add structured entries (Situation → Pattern → Move → Outcome → Guideline).

          REQUIRED SYSTEM PROMPT REVIEW AND OPTIONAL UPDATE
          1. SystemPromptTool_readSystemPrompt — review your system prompt to see if there are any new insights or patterns you want to add.
          2. SystemPromptTool_writeSystemPrompt — only if your core behavior charter needs a deliberate upgrade.

          OUTPUT DISCIPLINE
          • Provide a concise result recap, top lessons, and the exact updates you plan to make.
          • No tool calls needed in this reply, but specify which tools you will use next to capture learning.
          • Do not request user input; every action is driven by your analysis and the provided tools.

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
