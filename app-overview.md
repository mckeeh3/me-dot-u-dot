# Application Overview: me-dot-u-dot

**Target Audience:** AI models (LLMs) analyzing this codebase for understanding and potential improvements.

## Executive Summary

**me-dot-u-dot** is a research platform exploring self-learning AI agents through a grid-based strategy game. The core innovation is **zero-knowledge bootstrapping**: AI agents begin with no knowledge of game rules and must learn through gameplay, building tactical knowledge in a "playbook" and refining their behavioral approach via a modifiable system prompt. The platform demonstrates experience-driven learning, structured memory systems, and multi-agent interactions.

**Technical Stack:**
- Backend: Akka SDK (Java 21) with event sourcing, workflows, and agents
- Frontend: Vanilla HTML/JavaScript with Server-Sent Events
- AI Integration: OpenAI, Google Gemini, Anthropic Claude, local Ollama
- Architecture: Event-driven, CQRS, distributed-capable

## The Game Mechanics

### Board and Coordinates

The game is played on a square grid. Board size is determined by "level":

| Level | Grid Size | Coordinates Range |
|-------|-----------|-------------------|
| one   | 5×5       | A1 to E5         |
| two   | 7×7       | A1 to G7         |
| three | 9×9       | A1 to I9         |
| four  | 11×11     | A1 to K11        |
| five  | 13×13     | A1 to M13        |
| six   | 15×15     | A1 to O15        |
| seven | 17×17     | A1 to Q17        |
| eight | 19×19     | A1 to S19        |
| nine  | 21×21     | A1 to U21        |

Coordinates: Columns are letters (A-U), rows are numbers (1-21). A1 is top-left.

### Gameplay Flow

1. **Game Creation:** Two players (human or agent) are selected, board level is chosen
2. **Turn-Based Play:** Players alternate claiming unoccupied squares
3. **Scoring:** Moves score points based on patterns formed (see Scoring System)
4. **Game End:** Game ends when a player wins or board fills (draw)

### Scoring System

Players score points by forming patterns with their claimed squares. The scoring is complex and multi-dimensional:

#### Scoring Pattern Types:

1. **Horizontal Lines** - Consecutive squares in the same row
2. **Vertical Lines** - Consecutive squares in the same column
3. **Diagonal Lines** - Consecutive squares along diagonals (both directions)
4. **Adjacent Clusters** - Groups of adjacent squares (including diagonal adjacency)
5. **Top-to-Bottom Connections** - Connected path from top edge to bottom edge
6. **Left-to-Right Connections** - Connected path from left edge to right edge

#### Scoring Rules:

**Line Scoring (Horizontal, Vertical, Diagonal):**
- Minimum length required: `concurrentSquaresToScore = min(8, (boardSize / 2 + 1))`
  - Level one (5×5): 3 squares
  - Level two (7×7): 4 squares
  - Level three (9×9): 5 squares
  - Level four+ (11×11+): 8 squares (capped)
- Score = 1 point if move is at start or end of line
- Score = (line length - concurrentSquaresToScore + 1) if move connects two lines into one longer line
- Move must be part of the scoring line and positioned at start/end to score

**Adjacent Cluster Scoring:**
- Minimum cluster size: `adjacentSquaresToScore = min(8, concurrentSquaresToScore + 1)`
- Score = max(2, cluster size - 1) if cluster meets minimum size
- Adjacent means horizontally, vertically, or diagonally touching

**Edge-to-Edge Connections:**
- **Top-to-Bottom:** Score = 8 points for creating connected path from top row to bottom row
- **Left-to-Right:** Score = 8 points for creating connected path from left column to right column
- Path must be continuous (adjacent squares forming chain)

**Multiple Scoring Patterns:**
A single move can trigger multiple scoring patterns simultaneously. Total score for a move is the sum of all applicable pattern scores.

### Win/Loss Conditions

- **Win:** Player with highest score when game ends
- **Draw:** Equal scores when board fills
- **Forfeit:** Agent errors can cause automatic forfeit

### Important: Rules are NOT Documented for Agents

The game mechanics are intentionally **not provided** to AI agents. Agents must discover rules through:
- Trial and error (attempting moves and observing results)
- Analyzing move history and scoring patterns
- Building hypotheses in their playbook
- Iterative refinement across multiple games

## Agent Architecture

### Agent vs Agent Player Distinction

**Agent Implementation:**
- `AgentPlayerMakeMoveAgent` - Reusable capability for making moves
- `AgentPlayerPostGameReviewAgent` - Post-game analysis capability
- `AgentPlayerPlaybookReviewAgent` - Playbook refinement capability
- `AgentPlayerSystemPromptReviewAgent` - System prompt evolution capability

**Agent Player:**
- Concrete player record with unique ID, display name, and **fixed LLM model**
- Owns persistent state:
  - `PlaybookEntity` - Tactical knowledge document (player-authored)
  - `AgentRoleEntity` - System prompt / behavioral framework (player-authored)
- Each agent player starts with default system prompt and empty playbook
- Memory artifacts evolve independently per agent ID
- Model selection is permanent (cannot be changed after creation)

### Agent Learning Cycle

#### During Gameplay (Per Turn):

1. **Turn Initiation**
   - `DotGameToAgentWorkflowConsumer` receives `PlayerTurnCompleted` event
   - `AgentPlayerWorkflow` transitions to `makeMoveStep`

2. **Move Decision Phase**
   - `AgentPlayerMakeMoveAgent` is invoked with player's chosen LLM model
   - Agent receives user prompt: "TURN BRIEFING — YOUR MOVE" with opponent's last move details
   - Agent system prompt loaded from `AgentRoleEntity`

3. **Tool Execution Sequence**
   - **GameStateTool**: Retrieve current board state, scores, available squares
   - **PlaybookTools.readPlaybook**: Consult tactical memory
   - **SystemPromptTools.readSystemPrompt**: Review behavioral framework (optional, already in system context)
   - **MakeMoveTool**: Submit selected move

4. **Move Processing**
   - Move validated (square available, correct turn)
   - Scoring calculated for all applicable patterns
   - Board state updated
   - Events persisted: `MoveMade`, optionally `GameFinished`

5. **Turn Completion**
   - If human made the move: `PlayerTurnCompleted` event immediately emitted
   - If agent made the move: Workflow continues to next step
   - Next player's turn begins (or game ends)

#### Post-Game Review (After Game Ends):

1. **Initial Review Phase**
   - `AgentPlayerWorkflow` transitions to `startPostGameReviewStep`
   - `AgentPlayerPostGameReviewAgent` invoked with post-game analysis prompt
   - Agent uses tools:
     - `MoveHistoryTool`: Analyze full game move sequence
     - `MoveResponseLogsTool`: Review all move responses and scoring

2. **Playbook Update Phase**
   - `AgentPlayerWorkflow` transitions to `postGamePlaybookReviewStep`
   - `AgentPlayerPlaybookReviewAgent` invoked with playbook revision prompt
   - Agent receives post-game analysis from previous step
   - Agent uses tools:
     - `PlaybookTools.readPlaybook`: Retrieve current playbook
     - `PlaybookTools.writePlaybook`: Submit complete revised playbook
   - If agent updates playbook, change logged to `PlaybookJournalEntity`

3. **System Prompt Update Phase**
   - `AgentPlayerWorkflow` transitions to `postGameSystemPromptReviewStep`
   - `AgentPlayerSystemPromptReviewAgent` invoked with system prompt revision prompt
   - Agent receives post-game analysis and playbook update decisions
   - Agent uses tools:
     - `SystemPromptTools.readSystemPrompt`: Retrieve current system prompt
     - `SystemPromptTools.writeSystemPrompt`: Submit complete revised system prompt
   - If agent updates system prompt, change logged to `AgentRoleJournalEntity`

4. **Review Completion**
   - Workflow transitions to `postGameReviewCompletedStep`
   - Workflow completes, agent ready for next game

### Error Handling

**During Move Making:**
- **Recoverable errors** (ModelException, ModelTimeoutException, ToolCallExecutionException, JsonParsingException): Retry move (workflow retries)
- **Fatal errors** (RateLimitException, unknown exceptions): Forfeit move, emit `MoveForfeited` event, switch to opponent's turn

**During Post-Game Review:**
- **Playbook review failures**: Retry up to 3 times, then skip to system prompt review
- **System prompt review failures**: Retry up to 3 times, then mark review complete
- Agent continues functioning even if reviews fail

**Workflow Recovery:**
- `makeMoveStep` failures: Failover to `cancelGameStep` (game cancelled)
- Other step failures: Custom retry logic per step

## Memory Systems

### Playbook (Tactical Memory)

**Purpose:** Agent-authored document containing learned strategies, patterns, and tactical insights.

**Structure:**
- Completely free-form text (no imposed structure)
- Agents decide organization, format, and content
- Typically evolves to include:
  - Opening strategies
  - Scoring pattern recognition
  - Defensive tactics
  - Counter-move heuristics
  - Discovered rule patterns

**Operations:**
- `readPlaybook(agentId, gameId)` - Retrieve current playbook
- `writePlaybook(agentId, gameId, revisedContents)` - **Complete replacement** (not incremental)

**Evolution:**
- Starts empty for new agent players
- Updated after games (agent decides when/how)
- Full history preserved in `PlaybookJournalEntity` (read-only archive)
- Each version tagged with timestamp and sequence number

**Critical Design Constraint:**
Writing playbook is **full replacement**, not append or patch. Agent must:
1. Read current playbook
2. Incorporate new learnings
3. Submit complete revised document

### System Prompt (Behavioral Memory)

**Purpose:** Agent-authored framework defining approach to learning, decision-making, tool usage, and self-improvement.

**Structure:**
- Free-form text defining behavioral guidelines
- Loaded as system prompt for LLM invocations
- Typically includes:
  - Learning objectives and success metrics
  - Reasoning patterns and decision frameworks
  - Tool usage discipline
  - Self-improvement criteria
  - Meta-cognitive strategies

**Operations:**
- `readSystemPrompt(agentId, gameId)` - Retrieve current system prompt
- `writeSystemPrompt(agentId, gameId, revisedPrompt)` - **Complete replacement**

**Evolution:**
- Starts with shared default system prompt
- Updated after games (less frequently than playbook)
- Full history preserved in `AgentRoleJournalEntity` (read-only archive)
- Influences how agent learns and evolves

**Critical Design Constraint:**
System prompt is **active** during agent execution (not just memory). Changes affect:
- How agent reasons about moves
- How agent updates its own playbook
- How agent prioritizes learning objectives
- How agent uses tools

### Session Memory (Short-Term Context)

**Purpose:** Conversation history within single LLM invocation.

**Scope:**
- Single turn for `AgentPlayerMakeMoveAgent` (move decision)
- Single post-game review for `AgentPlayerPostGameReviewAgent`
- Single playbook revision for `AgentPlayerPlaybookReviewAgent`
- Single system prompt revision for `AgentPlayerSystemPromptReviewAgent`

**Contents:**
- System prompt (from `AgentRoleEntity`)
- User prompt (turn briefing or review prompt)
- Tool call history within invocation
- Tool response data

**Lifecycle:**
- Created per workflow step
- Persisted to `GameActionLogEntity` for audit
- Not carried between games or workflow steps

## Event Sourcing Architecture

### Core Entities (Event-Sourced)

**DotGameEntity:**
- **State:** Game board, player statuses, move history, scores, current turn
- **Commands:** CreateGame, MakeMove, PlayerTurnCompleted, ForfeitMove, CancelGame
- **Events:** GameCreated, MoveMade, PlayerTurnCompleted, MoveForfeited, GameCanceled, GameFinished, GameResults
- **Event Application:** Commands produce events, events update state (pure functions)

**PlaybookEntity:**
- **State:** Agent ID, current playbook instructions
- **Commands:** WritePlaybook
- **Events:** PlaybookWritten
- **Note:** Only stores latest version, journal stores history

**AgentRoleEntity:**
- **State:** Agent ID, current system prompt
- **Commands:** WriteSystemPrompt
- **Events:** SystemPromptWritten
- **Note:** Only stores latest version, journal stores history

**PlaybookJournalEntity:**
- **State:** Agent ID, sequence-numbered list of all playbook versions
- **Events:** PlaybookJournalEntryAdded
- **Purpose:** Read-only historical archive for observation/analysis

**AgentRoleJournalEntity:**
- **State:** Agent ID, sequence-numbered list of all system prompt versions
- **Events:** AgentRoleJournalEntryAdded
- **Purpose:** Read-only historical archive for observation/analysis

**PlayerEntity (Key-Value, not Event-Sourced):**
- **State:** Player ID, name, type (human/agent), model
- **Operations:** CreatePlayer, GetPlayer
- **Note:** Simple CRUD, no event history

**PlayerGamesEntity:**
- **State:** Aggregated player statistics (games played, wins, losses, total score)
- **Events:** PlayerGamesUpdated
- **Purpose:** Leaderboard data

**GameActionLogEntity & GameMoveLogEntity:**
- **State:** Timestamped logs of all actions (tool calls, prompts, responses)
- **Purpose:** Audit trail, debugging, guardrail monitoring

### Consumers (Event Stream Processing)

**DotGameToAgentWorkflowConsumer:**
- Listens: `DotGameEntity` events
- Triggers: `AgentPlayerWorkflow` for agent turns
- Logic: Filters `PlayerTurnCompleted` events, identifies agent players, starts workflow

**PlaybookToPlaybookJournalConsumer:**
- Listens: `PlaybookEntity` events
- Triggers: `PlaybookJournalEntity` updates
- Logic: Archives every playbook change with sequence number

**AgentRoleToAgentRoleJournalConsumer:**
- Listens: `AgentRoleEntity` events
- Triggers: `AgentRoleJournalEntity` updates
- Logic: Archives every system prompt change with sequence number

**DotGameToPlayerGamesConsumer:**
- Listens: `DotGameEntity` events
- Triggers: `PlayerGamesEntity` updates
- Logic: Updates player statistics on game completion

### Views (CQRS Query Models)

**DotGameView:**
- Projection: Game states for querying
- Use: Display game list, retrieve game details

**PlaybookJournalView:**
- Projection: Historical playbook versions
- Use: "View Journal" feature in web UI

**AgentRoleJournalView:**
- Projection: Historical system prompt versions
- Use: Agent role evolution viewer

**PlayerView:**
- Projection: Player profiles
- Use: Player selection, player management

**PlayerGamesView:**
- Projection: Player statistics and rankings
- Use: Leaderboard display

**GameActionLogView:**
- Projection: Audit logs for games
- Use: Game action log viewer, debugging

## Workflow Orchestration

### AgentPlayerWorkflow

**Purpose:** Coordinates agent turn execution and post-game learning phases.

**State:** Agent player info, game ID, session ID prefix, move count tracker

**Steps:**

1. **makeMoveStep** (input: `PlayerTurnCompleted` event)
   - Invoke `AgentPlayerMakeMoveAgent.makeMove`
   - Wait for move completion
   - On success: Wait for next `PlayerTurnCompleted` event
   - On failure: Failover to `cancelGameStep`

2. **startPostGameReviewStep** (input: `PlayerTurnCompleted` event with game ended)
   - Invoke `AgentPlayerPostGameReviewAgent.postGameReview`
   - Capture analysis output
   - Transition to `postGamePlaybookReviewStep`
   - On failure: Retry up to 3 times, then skip to `postGameReviewCompletedStep`

3. **postGamePlaybookReviewStep** (input: post-game analysis)
   - Invoke `AgentPlayerPlaybookReviewAgent.reviewPlaybook`
   - Agent may update playbook via tools
   - Transition to `postGameSystemPromptReviewStep`
   - On failure: Retry up to 3 times, then skip to next step

4. **postGameSystemPromptReviewStep** (input: post-game analysis + playbook decisions)
   - Invoke `AgentPlayerSystemPromptReviewAgent.reviewSystemPrompt`
   - Agent may update system prompt via tools
   - Transition to `postGameReviewCompletedStep`
   - On failure: Retry up to 3 times, then skip to completion

5. **postGameReviewCompletedStep**
   - Mark workflow complete
   - Workflow ends

6. **cancelGameStep** (failover target)
   - Invoke `DotGameEntity.cancelGame`
   - Mark workflow complete

**Configuration:**
- Default step timeout: 10 minutes
- Default recovery: Cancel game on failure
- Custom recovery per step (retries, failovers)

## Tools Available to Agents

### GameStateTool

**Function:** `getGameState(gameId, agentId)`

**Returns:**
```json
{
  "gameInfo": {
    "gameId": "...",
    "status": "in progress"
  },
  "cumulativeScore": {
    "you": 5,
    "opponent": 3
  },
  "activePlayer": {
    "who": "you",
    "playerId": "..."
  },
  "boardInfo": {
    "level": "three",
    "topLeftSquare": {"squareId": "A1", "row": 1, "column": 1},
    "bottomRightSquare": {"squareId": "I9", "row": 9, "column": 9}
  },
  "availableSquares": {
    "availableSquareIds": ["A1", "B2", "C3", ...]
  },
  "moveHistory": {
    "moves": [
      {
        "squareId": "E5",
        "who": "you",
        "playerId": "...",
        "moveScore": 2,
        "scoringMoves": [
          {
            "moveSquareId": "E5",
            "type": "horizontal line",
            "score": 1,
            "scoringSquareIds": ["E3", "E4", "E5"]
          },
          {
            "moveSquareId": "E5",
            "type": "vertical line",
            "score": 1,
            "scoringSquareIds": ["C5", "D5", "E5"]
          }
        ]
      }
    ]
  }
}
```

**Purpose:** Authoritative source for current game state.

**Usage Pattern:** Called at start of every turn to understand board state.

### MakeMoveTool

**Function:** `makeMove(gameId, agentId, squareId)`

**Input:** Single square coordinate (e.g., "C3")

**Returns:**
```json
{
  "moveDetails": {
    "squareId": "C3",
    "moveWas": "completed",
    "reason": "Legal move, moved to an available square"
  },
  "cumulativeScore": {
    "you": 7,
    "opponent": 3
  },
  "moveScore": {
    "delta": 2,
    "scoringMoves": [
      {
        "moveSquareId": "C3",
        "type": "horizontal line",
        "score": 1,
        "scoringSquareIds": ["C1", "C2", "C3"]
      },
      {
        "moveSquareId": "C3",
        "type": "adjacent",
        "score": 1,
        "scoringSquareIds": ["B2", "B3", "C2", "C3"]
      }
    ]
  },
  "activePlayer": {
    "who": "opponent",
    "playerId": "...",
    "reason": "It's your opponent's turn"
  }
}
```

**Purpose:** Submit move and receive immediate feedback.

**Rejection Cases:**
- Square already occupied: `"moveWas": "rejected", "reason": "Illegal move, square C3 is not available"`
- Wrong turn: `"moveWas": "rejected", "reason": "Illegal move, it's not your turn"`

**Usage Pattern:** Called after analyzing game state to submit chosen move.

### PlaybookTools

**Functions:**
- `readPlaybook(agentId, gameId)` → `{instructions: "..."}` (string, may be empty)
- `writePlaybook(agentId, gameId, revisedPlaybookContents)` → `Done`

**Purpose:** Persistent tactical memory.

**Critical Constraints:**
- Write is **full replacement**, not incremental
- Must read first, then modify, then write complete document
- Agent fully controls format and structure

**Usage Patterns:**
- During move: Read playbook to consult learned strategies
- Post-game: Read playbook, integrate new learnings, write revised version

### SystemPromptTools

**Functions:**
- `readSystemPrompt(agentId, gameId)` → `{systemPrompt: "..."}` (string)
- `writeSystemPrompt(agentId, gameId, revisedPrompt)` → `Done`

**Purpose:** Behavioral framework memory.

**Critical Constraints:**
- Write is **full replacement**
- Agent defines own learning approach, tool usage discipline, success metrics
- Changes affect future behavior (loaded as system prompt in next invocations)

**Usage Patterns:**
- During move: Already loaded as system prompt (read usually unnecessary)
- Post-game: Read current prompt, refine approach based on experience, write revised version

### MoveHistoryTool

**Function:** `getMoveHistory(gameId, agentId)`

**Returns:** Full move sequence with scoring details (similar to `GameStateTool.moveHistory` but with additional context)

**Purpose:** Post-game analysis of full game trajectory.

**Usage Pattern:** Called during post-game review to analyze patterns.

### MoveResponseLogsTool

**Function:** `getMoveResponseLogs(gameId, agentId)`

**Returns:** Chronological list of all move responses received during game.

**Purpose:** Review detailed scoring feedback for pattern recognition.

**Usage Pattern:** Called during post-game review to understand scoring mechanics.

## Web Interface

### Main Game UI (`/index.html`)

**Setup Phase:**
1. Select or create Player 1 (human or agent)
2. Select or create Player 2 (human or agent)
3. Choose board level (one through nine)
4. Click "Begin" to start game

**Gameplay Phase:**
- Board rendered as interactive grid
- Click square to make move (human players)
- Real-time updates via Server-Sent Events
- Move timer tracks think time
- Score display for both players
- Audio feedback for moves, wins, losses

**Agent Player Creation:**
- Requires: Player ID, Player Name, Model selection
- Models pulled from `application.conf` (all configured models shown)
- Playbook and system prompt initialized automatically

### Playbook Viewer (`/playbook.html`)

**Features:**
- Select agent player from dropdown
- View complete playbook journal (all versions)
- Pagination through historical versions
- Diff view showing changes between versions
- Timestamp and sequence number for each version

**Purpose:** Observe agent learning progression without influencing behavior.

### Agent Role Viewer (`/agent-role.html`)

**Features:**
- Select agent player from dropdown
- View complete system prompt journal (all versions)
- Diff-friendly UI highlighting additions/removals
- Cross-reference with playbook changes
- Timestamp and sequence number for each version

**Purpose:** Track behavioral evolution alongside tactical learning.

### Leader Board (`/leader-board.html`)

**Features:**
- Rankings by wins, total score, win rate
- Player statistics (games played, wins, losses)
- Click player to view game history
- Click game to view details
- Scroll icons to jump to player in other views

**Purpose:** Compare agent performance across games and models.

### Game Action Log Viewer (`/game-action-log.html`)

**Features:**
- Recent games list with player info
- Action stream: All tool calls, prompts, responses in chronological order
- Detail pane: Full payload for selected action (JSON formatted)
- Guardrail events: Flagged suspicious behavior
- Pagination for long games
- Cross-navigation to leader board

**Purpose:** Debugging, auditing, verifying tool discipline, understanding agent decisions.

## Configuration System

### Model Configuration (`application.conf`)

**Naming Convention:** `ai-agent-model-<unique-model-key-name>`
- **NO periods** in model names (use hyphens or underscores)
- Model key must match player model selection

**Example Configuration:**
```hocon
ai-agent-model-gpt-4o = ${akka.javasdk.agent.openai}
ai-agent-model-gpt-4o {
  provider = "openai"
  api-key = ${?OPENAI_API_KEY}
  model-name = "gpt-4o"
  base-url = "https://api.openai.com/v1"
  temperature = 1.0
  top-p = 1.0
  max-tokens = 16384
  max-completion-tokens = 200000
}
```

**Supported Providers:**
- `openai` - OpenAI models (GPT-4, GPT-5, O3, etc.)
- `googleai-gemini` - Google Gemini models
- `anthropic` - Anthropic Claude models
- `ollama` - Local Ollama models

**Environment Variables:**
- `OPENAI_API_KEY`
- `GOOGLE_AI_GEMINI_API_KEY`
- `ANTHROPIC_API_KEY`

### Runtime Configuration

**Dev Mode:**
- Persistence disabled by default: `akka.javasdk.dev-mode.persistence.enabled = false`
- HTTP port: 9000 (default), configurable via `akka.javasdk.dev-mode.http-port`
- External access: `-Dakka.runtime.http-interface=0.0.0.0`

## Key Design Patterns & Principles

### Event Sourcing

**Benefits:**
- Complete audit trail of all game actions
- Time-travel debugging (replay events)
- Eventual consistency across distributed nodes
- Natural separation of write commands and read queries (CQRS)

**Implementation:**
- Commands validated against current state
- Events emitted as facts (past tense)
- State rebuilt by applying events sequentially
- Views project events into query-optimized models

### Agent Tool Discipline

**Pattern:** Agents **must** call tools in specific sequence each turn:
1. GameStateTool (understand state)
2. PlaybookTools (consult memory)
3. MakeMoveTool (execute decision)

**Enforcement:**
- Not programmatically enforced (agents can violate)
- System prompt guides behavior
- Logs capture violations for analysis

**Violations Observed:**
- Skipping GameStateTool (guessing state)
- Multiple MakeMoveTool calls (exploring options)
- Forgetting to call MakeMoveTool (incomplete turn)

### Zero-Knowledge Learning

**Philosophy:** Agents discover game mechanics through experimentation.

**Implementation:**
- No rules provided in system prompt or tools
- Tool descriptions are minimal (e.g., MakeMoveTool says "make a move", not "score by forming lines")
- Feedback is factual (scoring details) but not explanatory (no "because you formed a horizontal line")

**Learning Trajectory:**
1. **Random moves** - No pattern, pure exploration
2. **Rule discovery** - Recognizing square availability, turn-taking
3. **Pattern recognition** - Identifying scoring moves (lines, clusters)
4. **Strategic play** - Offensive (scoring) vs defensive (blocking)
5. **Advanced tactics** - Multi-turn planning, opponent modeling

### Memory Architecture

**Two-Tier Memory:**
1. **Playbook (Tactical)** - What to do, when, why
2. **System Prompt (Behavioral)** - How to learn, think, improve

**Design Rationale:**
- Playbook changes frequently (every game or multiple times per game)
- System prompt changes infrequently (after observing learning quality issues)
- Separation allows meta-learning (improving learning process itself)

**Alternative Considered:** Single memory document
- **Rejected:** Conflates tactics with meta-cognition, harder to diagnose learning issues

### Journal Systems (Observability)

**Pattern:** Separate read-only archive entities for playbook and system prompt evolution.

**Purpose:**
- Research: Analyze learning trajectories
- Debugging: Understand when/why agent behavior changed
- Comparison: Side-by-side evolution of different agents/models

**Critical Constraint:** Journals are **not used** by agents during gameplay.
- Agents only access latest version via `PlaybookEntity` / `AgentRoleEntity`
- Journal access is human-only (via web UI)

## Observability & Debugging

### Game Action Log

**Data Captured:**
- Every tool call with full request/response
- Model prompts sent to LLM
- Model responses from LLM
- Timestamps, agent IDs, game IDs
- Guardrail events (flagged suspicious behavior)

**Use Cases:**
- Debugging agent errors
- Verifying tool discipline
- Understanding agent reasoning
- Detecting prompt injection or manipulation attempts

### Guardrails

**Monitoring:**
- Playbook/system prompt updates flagged if suspicious
- Logged to action log with "guardrail" event type
- Append-only fallback (suspicious update rejected, logged)

**Criteria for Flagging:**
- Null or empty updates
- Updates identical to current version
- Updates with suspicious patterns (TBD: specific patterns not in current code)

### Workflow Observability

**Logging:**
- Each workflow step logs entry/exit
- Error logs capture exception details
- Retry attempts tracked

**Monitoring:**
- Step timeouts (default 10 minutes)
- Retry counts
- Failover invocations

## Multi-Model Comparison

### Experiment Design

**Controlled Variables:**
- Same game (board level, opponent)
- Same initial system prompt (shared default)
- Same tool set and descriptions
- Same workflow logic

**Varied Variable:**
- LLM model provider and version

**Measurable Outcomes:**
1. **Learning rate:** Games to reach proficiency (e.g., average score > threshold)
2. **Response latency:** Time per move (logged as `thinkMs`)
3. **Cost:** Tokens used per game (model-dependent pricing)
4. **Learning style:** Playbook structure, update frequency, verbosity
5. **Strategic sophistication:** Move quality, pattern recognition, opponent modeling
6. **Tool discipline:** Violations of expected tool usage patterns
7. **Error rate:** Forfeits, illegal moves, timeouts

### Observations (from README)

**Response Latency:**
- "Even fast models often require several seconds, and sometimes minutes"
- Variability across providers for identical tasks

**Learning Behavior:**
- Models vary in playbook rewrite aggressiveness
- Different strategies for consolidating experience
- Some models update frequently (incremental), others rarely (batch)

**Operational Cost:**
- Directly comparable (same tool sequence every turn)
- Cost differences visible across games

## Areas for Potential Improvement (Research Directions)

### 1. Learning Efficiency

**Current State:** Agents may require many games to discover scoring rules.

**Potential Improvements:**
- **Curriculum learning:** Start with smaller boards (level one), graduate to larger
- **Hint system:** Optionally provide partial rule explanations after N failed attempts
- **Comparative learning:** Allow agent to observe other agent games (not just own)
- **Reward shaping:** Provide intermediate feedback (e.g., "good defensive move" even if no score)

**Risks:**
- Hints may reduce zero-knowledge authenticity
- Curriculum may not transfer (small board tactics != large board tactics)

### 2. Memory System Enhancements

**Current State:** Flat text documents, agent decides structure.

**Potential Improvements:**
- **Structured playbook schema:** JSON format with sections (openings, endgame, patterns)
- **Vector memory:** Embed playbook entries, retrieve relevant context by similarity
- **Multi-tier memory:** Short-term (session), medium-term (recent games), long-term (playbook)
- **Memory compression:** Summarize old entries, prune redundant information
- **Shared memory:** Pool of common knowledge across agent players (with attribution)

**Risks:**
- Imposed structure may limit agent creativity
- Vector retrieval adds complexity, may degrade with sparse data
- Shared memory reduces independence (contaminated experiments)

### 3. Tool Design

**Current State:** Minimal tool descriptions, no rule explanations.

**Potential Improvements:**
- **Exploratory tools:** `whatIfMove(squareId)` - simulate move without committing
- **Pattern analysis tools:** `analyzePattern(squareIds)` - check if pattern scores
- **Opponent modeling tools:** `getOpponentMoves()` - filtered opponent history
- **Self-reflection tools:** `evaluatePlaybook()` - LLM critiques own playbook

**Risks:**
- More tools = higher latency, cost
- Overpowered tools reduce learning challenge
- Self-reflection may loop infinitely

### 4. Multi-Agent Learning

**Current State:** Agents learn only from own games.

**Potential Improvements:**
- **Agent collaboration:** Multiple agents share playbook, vote on moves
- **Agent competition:** Tournament structure with rankings
- **Agent mentorship:** Expert agent guides novice (co-play or commentary)
- **Agent debates:** Two agents argue for different moves, third decides

**Risks:**
- Collaboration may homogenize strategies
- Competition may require many agents (resource intensive)

### 5. Adaptive System Prompts

**Current State:** Agent manually updates system prompt post-game.

**Potential Improvements:**
- **Automatic prompt engineering:** Meta-agent optimizes system prompt based on performance
- **A/B testing framework:** Run multiple system prompt variants, compare outcomes
- **Prompt versioning:** Git-like branches/merges for prompt evolution
- **Prompt constraints:** Enforce sections (learning objectives, tool usage, decision framework)

**Risks:**
- Meta-optimization may overfit to specific games
- A/B testing requires many games for statistical significance

### 6. Scoring System Transparency

**Current State:** Scoring is opaque (zero-knowledge).

**Potential Improvements:**
- **Progressive disclosure:** Reveal one scoring type at a time (e.g., "horizontal lines unlock at game 5")
- **Tutorial mode:** Explicit rule explanations for first N games
- **Hint on failure:** If agent scores 0 for many moves, suggest "try forming lines"

**Risks:**
- Reduces research value (no longer zero-knowledge)
- May create dependency on hints (not generalizable)

### 7. Real-Time Learning

**Current State:** Learning happens post-game only.

**Potential Improvements:**
- **In-game playbook updates:** Agent updates playbook mid-game after key insights
- **Continuous learning:** Update playbook after every move
- **Reflective turns:** Agent takes extra time to analyze before critical moves

**Risks:**
- Mid-game updates may destabilize strategy
- Continuous updates increase latency, cost
- May violate workflow assumptions (playbook changes during active session)

### 8. Opponent Modeling

**Current State:** Agents receive opponent move data but no explicit opponent analysis.

**Potential Improvements:**
- **Opponent profile:** Track opponent patterns, predict next move
- **Adaptive strategy:** Change tactics based on opponent type (human vs agent, aggressive vs defensive)
- **Counter-strategy memory:** Store opponent-specific tactics in playbook

**Implementation:**
- Add `OpponentProfile` to playbook structure
- Tool: `analyzeOpponent(opponentId)` - retrieve opponent history, patterns
- Update playbook with opponent-specific notes

### 9. Visualization & Interpretability

**Current State:** Logs are text/JSON, journals show full document history.

**Potential Improvements:**
- **Strategy visualization:** Heatmaps of preferred squares, move patterns
- **Learning curve graphs:** Score over time, win rate progression
- **Playbook diffs:** Side-by-side comparison with highlights
- **Decision tree extraction:** Visualize agent's internal decision logic (if extractable)

**Implementation:**
- Frontend: Add analytics dashboard
- Backend: Aggregate statistics in new view entities

### 10. Error Recovery

**Current State:** Forfeits on fatal errors, retries on recoverable errors.

**Potential Improvements:**
- **Graceful degradation:** If tool fails, use fallback (e.g., random legal move)
- **Error explanation:** Include error details in next turn prompt ("last move failed because...")
- **Self-diagnosis:** Agent analyzes own errors, updates playbook with "avoid X" rules

**Risks:**
- Fallbacks may mask tool issues
- Error explanations increase context length

### 11. Game Variants

**Current State:** Single scoring system, single game mode.

**Potential Improvements:**
- **Alternative scoring:** Different point values, different patterns
- **Team play:** 2v2 agents
- **Fog of war:** Hide opponent moves until adjacent
- **Dynamic boards:** Squares disappear or change ownership

**Risks:**
- Variants fragment research focus
- May require significant code changes

### 12. Performance Optimization

**Current State:** Event sourcing may have latency, LLM calls are slow.

**Potential Improvements:**
- **Caching:** Cache common game state queries
- **Parallel tool calls:** Execute GameStateTool and PlaybookTools simultaneously
- **Smaller models for some steps:** Use fast model for simple tasks, smart model for complex decisions
- **Precompute patterns:** Cache scoring pattern lookups per board state

**Risks:**
- Caching may cause staleness bugs
- Parallel calls may violate Akka SDK assumptions
- Mixed models complicate comparison experiments

## Technical Debt & Known Issues

### 1. Tool Discipline Not Enforced

**Issue:** Agents can skip tools or call in wrong order.

**Impact:** Inconsistent behavior, hard to compare agents.

**Solution:** Workflow-level enforcement (require GameStateTool before MakeMoveTool).

### 2. Playbook Write is Full Replacement

**Issue:** Agent must resubmit entire playbook, even for small changes.

**Impact:** Context length bloat, higher cost, potential truncation.

**Solution:** Incremental update API (append, patch, delete sections).

### 3. Journal Entities Grow Unbounded

**Issue:** Every playbook/system prompt version stored forever.

**Impact:** Storage growth, query slowdown over time.

**Solution:** Archival policy (compress old entries, move to cold storage).

### 4. No Agent Concurrency Control

**Issue:** If two games run simultaneously for same agent, playbook updates may conflict.

**Impact:** Last-write-wins, potential loss of learning.

**Solution:** Optimistic locking with version numbers, reject stale updates.

### 5. Scoring Calculation in Domain Logic

**Issue:** Scoring is computed in `DotGame` domain model, not extractable.

**Impact:** Hard to test scoring in isolation, hard to explain to agents.

**Solution:** Extract scoring rules to separate `ScoringEngine` component.

### 6. Error Messages Not Actionable

**Issue:** Tool errors like "Illegal move, it's not your turn" don't guide agent to correct behavior.

**Impact:** Agent may repeat same error multiple times.

**Solution:** Include actionable guidance ("It's not your turn. Wait for opponent's move before trying again").

### 7. Guardrail Implementation Incomplete

**Issue:** Guardrail logic mentioned in docs but not fully implemented in code.

**Impact:** Cannot detect prompt injection or manipulation attempts.

**Solution:** Implement heuristics (null checks, suspicious keywords, embedding similarity).

## Conclusion: Research Value & Future Directions

**me-dot-u-dot** provides a controlled environment for studying self-learning AI agents. Key research questions it enables:

1. **Can LLMs learn complex rule systems from zero knowledge?**
   - Current evidence: Yes, but slowly and with high variance

2. **How do different models approach learning?**
   - Observable via playbook structure, update frequency, strategic sophistication

3. **What memory architectures optimize learning?**
   - Current: Flat text playbooks
   - Future: Structured, vector-based, hierarchical

4. **Can agents improve their own learning processes?**
   - Observable via system prompt evolution, meta-cognitive strategies

5. **How do agents handle multi-objective optimization?**
   - Offensive vs defensive play, short-term vs long-term gains

The platform's extensibility (pluggable models, configurable game rules, observable learning progression) makes it a valuable testbed for agent learning research. The clean separation of agent implementation (reusable capability) and agent players (independent learners) enables rigorous multi-model comparison under identical conditions.

**Primary value proposition:** Observe emergent learning strategies in AI agents without prescribing approaches, while maintaining scientific rigor through controlled variables and comprehensive observability.
