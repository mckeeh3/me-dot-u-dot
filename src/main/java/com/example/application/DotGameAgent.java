package com.example.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.DotGame;
import com.example.domain.DotGame.PlayerStatus;
import com.example.domain.Playbook;

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
    log.debug("MakeMovePrompt: {}", prompt);
    gameLog.logModelPrompt(prompt.gameId, prompt.player().player().id(), prompt.toPrompt());

    return effects()
        .model(ModelProvider.fromConfig("ai-agent-model-" + prompt.player().player().model()))
        .tools(functionTools)
        .systemMessage(systemPrompt(prompt.player().player().id()))
        .userMessage(prompt.toPrompt())
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
    return "Try again, possible recoverable agent error, agent: %s, agent error: %s".formatted(prompt.player().player().id(), exception.getMessage());
  }

  String forfeitMoveDueToError(MakeMovePrompt prompt, Throwable exception) {
    log.error("Forfeiting move due to agent error: %s".formatted(exception.getMessage()), exception);

    var message = "Agent: %s, forfeited move due to agent error: %s".formatted(prompt.player().player().id(), exception.getMessage());
    var command = new DotGame.Command.ForfeitMove(prompt.gameId, prompt.player().player().id(), message);

    componentClient
        .forEventSourcedEntity(prompt.gameId)
        .method(DotGameEntity::forfeitMove)
        .invoke(command);

    return "Forfeited move due to agent error: %s".formatted(exception.getMessage());
  }

  String gameStateAsJson(String gameId) {
    var result = componentClient
        .forEventSourcedEntity(gameId)
        .method(DotGameEntity::getState)
        .invoke();

    if (result instanceof DotGame.State) {
      return JsonSupport.encodeToString(GameStateTool.CompactGameState.from((DotGame.State) result));
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
      PlayerStatus player,
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
            """.formatted(player.player().id(), gameId, status)
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
          """.formatted(player.isWinner() ? "won" : "lost", player.player().id(), gameId, status)
          .stripIndent();
    }

    public String toPrompt() {
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
            """
            .formatted(
                gameId(),
                player().player().id(),
                status().name(),
                player().score(),
                opponentScore())
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
          """
          .formatted(
              gameId(),
              player().player().id(),
              player().isWinner() ? "You Won" : "You lost",
              player().score(),
              opponentScore())
          .stripIndent();
    }
  }
}
