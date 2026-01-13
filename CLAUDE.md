# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**me-dot-u-dot** is a research platform for exploring self-learning AI agent techniques through interactive gameplay. Agents begin with zero knowledge of game rules and progressively learn strategy through experience and structured memory systems (playbooks and system prompts). The project demonstrates zero-knowledge bootstrapping, experience-driven learning, and multi-agent interactions.

Built with **Akka SDK** (Java 21), the application uses event sourcing, agents, views, and consumers for scalable, event-driven architecture. The frontend is vanilla HTML/JavaScript with Server-Sent Events for real-time updates.

## Build & Development Commands

### Running the Application
```bash
# Start the Akka runtime locally
mvn compile exec:java

# Expose service externally (0.0.0.0)
mvn compile exec:java -Dakka.runtime.http-interface=0.0.0.0

# Web interface
http://localhost:9000
```

### Testing
```bash
# Run all tests
mvn test

# Full build with tests
mvn clean verify
```

### Helper Scripts
The repository root contains shell scripts for manual API testing:
- `./cancel-game.sh <gameId>` - Cancel a game
- `./get-games-by-player.sh <playerId>` - Get player game history
- `./leader-board.sh` - View leader board
- `./list-openai-models.sh` / `list-gemini-models.sh` / `list-anthropic-models.sh` - List available models
- `./agent-role-reset.sh` - Reset agent role/system prompt
- `./make-move-tool-test.sh` - Test move tool

## Architecture Overview

### Code Structure
```
src/main/java/com/example/
├── domain/          # Core game logic, domain models, events & commands
│   ├── DotGame.java       # Game state, moves, scoring, board logic
│   ├── Playbook.java      # Agent tactical instructions
│   ├── AgentRole.java     # Agent system prompt
│   ├── Player.java        # Player identities
│   └── PlayerGames.java   # Player statistics aggregation
├── application/     # Akka SDK components (entities, agents, tools, consumers)
│   ├── DotGameEntity.java              # Event-sourced game state
│   ├── AgentPlayerWorkflow.java        # Orchestrates agent turns & learning
│   ├── AgentPlayerMakeMoveAgent.java   # LLM agent for move decisions
│   ├── AgentPlayerPostGameReviewAgent.java  # Post-game analysis
│   ├── PlaybookEntity.java             # Per-agent playbook storage
│   ├── AgentRoleEntity.java            # Per-agent system prompt storage
│   ├── DotGameView.java                # Query model for games
│   └── *Consumer.java                  # Event stream processors
└── api/            # HTTP endpoints (REST)
    ├── GameEndpoint.java
    ├── PlayerEndpoint.java
    └── PlaybookEndpoint.java

src/main/resources/
├── application.conf         # LLM model configurations
└── static-resources/        # Frontend HTML/CSS/JS
```

### Key Akka Components

**Event Sourced Entities:**
- `DotGameEntity` - Game state management via event sourcing
- `PlaybookEntity` / `PlaybookJournalEntity` - Agent learning memory (tactical instructions)
- `AgentRoleEntity` / `AgentRoleJournalEntity` - Agent system prompt evolution
- `PlayerEntity` / `PlayerGamesEntity` - Player profiles and statistics
- `GameActionLogEntity` / `GameMoveLogEntity` - Audit trails

**Akka Workflows:**
- `AgentPlayerWorkflow` - Orchestrates agent turns, handling move-making and post-game reviews. Uses step-based execution with failover to game cancellation on critical errors.

**Akka Agents (LLM Integration):**
- `AgentPlayerMakeMoveAgent` - Makes gameplay decisions using tools
- `AgentPlayerPostGameReviewAgent` - Analyzes completed games
- `AgentPlayerPlaybookReviewAgent` - Reviews/updates playbook
- `AgentPlayerSystemPromptReviewAgent` - Reviews/updates system prompt

**Agent Tools (exposed to LLMs):**
- `GameStateTool` - Retrieves current board, players, scores, move history
- `MakeMoveTool` - Submits agent's selected move
- `PlaybookTools` - Reads/updates agent playbook instructions
- `SystemPromptTools` - Reads/updates agent system prompt
- `MoveHistoryTool` - Retrieves move history for analysis

**Views (Query Models):**
- `DotGameView`, `PlaybookJournalView`, `AgentRoleJournalView`, `PlayerView`, `PlayerGamesView` - Project event streams into SQL-addressable tables for queries

**Consumers (Event Stream Processing):**
- `DotGameToAgentWorkflowConsumer` - Bridges game events to agent workflow
- `PlaybookToPlaybookJournalConsumer` - Archives playbook evolution
- `AgentRoleToAgentRoleJournalConsumer` - Archives system prompt evolution

### Agent Learning Flow

1. **Game Event** - `DotGameEntity` processes a move and emits `PlayerTurnCompleted` event
2. **Consumer Activation** - `DotGameToAgentWorkflowConsumer` receives the event
3. **Workflow Orchestration** - `AgentPlayerWorkflow` transitions to `makeMoveStep`
4. **Agent Invocation** - `AgentPlayerMakeMoveAgent` is invoked with the player's model and tools
5. **Tool Execution** - Agent calls `GameStateTool`, consults playbook/system prompt via `PlaybookTools`/`SystemPromptTools`, then calls `MakeMoveTool`
6. **Learning Update** - After game completion, agent may update playbook and/or system prompt
7. **Journal Recording** - `PlaybookJournalEntity` and `AgentRoleJournalEntity` record evolution

### Agent Players vs Agent Implementation

- **Agent implementation** (`AgentPlayerMakeMoveAgent`) is reusable capability encapsulating LLM session invocation and tool availability
- **Agent players** are concrete player records (unique ID, display name, chosen LLM model) with persistent state:
  - `PlaybookEntity` - Per-agent tactical instructions
  - `AgentRoleEntity` - Per-agent system prompt
- Each agent player starts with shared default system prompt and empty playbook, but these evolve independently per agent ID
- Player record permanently captures chosen LLM model (cannot be changed after creation)
- Multiple agent players using different models can be compared side-by-side for learning behavior, latency, and cost

## Configuration

### LLM Model Setup

Agent models are configured in `src/main/resources/application.conf` using the naming pattern:
```
ai-agent-model-<unique-model-key-name>
```

**DO NOT use '.' (period) in model names** - use underscores or hyphens instead.

API keys are consumed via environment variables:
- `OPENAI_API_KEY` - For OpenAI models (GPT-4o, GPT-5, etc.)
- `GOOGLE_AI_GEMINI_API_KEY` - For Google Gemini models
- `ANTHROPIC_API_KEY` - For Anthropic Claude models

Example model configuration structure:
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
}
```

Supported providers: `openai`, `googleai-gemini`, `anthropic`, `ollama`

## Development Guidelines

### Coding Style
- Java 21 with two-space indentation
- Prefer fluent Akka patterns: `effects().persistAll(...)`
- Use `var` for obvious local type inference
- UpperCamelCase for classes/records, lowerCamelCase for methods/fields, UPPER_SNAKE_CASE for constants
- REST paths: kebab-case, JSON fields: camelCase

### Testing
- JUnit 5 tests in `src/test/java` with matching packages
- Test suffix: `*Test` (e.g., `DotGameEntityTest`)
- Cover both command handling and event application for entities
- Run `mvn test` before committing
- Keep scenarios deterministic

### Commits
- Short, present-tense titles under 60 characters
- Examples from git log: `"remove old scoring code"`, `"add scoring moves to game state tool"`

### Security
- Never commit API keys
- Use environment variables in `application.conf`
- Document new configuration toggles inline

## Key Concepts

### Event Sourcing Pattern
Domain models (e.g., `DotGame.State`) implement command handlers (`onCommand`) that return events, and event handlers (`onEvent`) that produce new state. Entities persist events, not state directly.

### Agent Tool Discipline
LLM agents **must** call provided tools each turn. The workflow enforces this pattern:
1. Read game state (`GameStateTool`)
2. Consult memory (`PlaybookTools`, `SystemPromptTools`)
3. Make move (`MakeMoveTool`)
4. Optionally update memory (playbook/system prompt)

### Playbook & Role Journal Systems
Journals are **observational only** - not used by agents during gameplay. They provide:
- Complete learning history timeline
- Decision archaeology (trace how insights developed)
- Comparative analysis between agents
- Accessible via web interface "View Journal" feature

### Game Action Log Viewer
`/game-action-log.html` provides audit trail:
- Recent games list with leader-board integration
- Action stream with timestamps, actors, tool invocations
- Detail pane for full payloads (model prompts, responses, guardrail events)
- Invaluable for debugging agent behavior and verifying tool discipline

## Monitoring & Observability

- View agent learning progression: Web interface "View Journal" for any AI player
- View agent role evolution: `/agent-role.html` with diff-friendly UI
- Game action log: `/game-action-log.html` for detailed audit trail
- Leader board: `/leader-board.html` for player statistics

## Reference Documentation

- Akka Java SDK: https://doc.akka.io/java/
- Model provider configurations: https://doc.akka.io/java/model-provider-details.html
- OpenAI models: https://platform.openai.com/docs/models/overview
- Project README.md contains detailed conceptual overview and research applications
- AGENTS.md contains repository guidelines for build, test, style, and configuration conventions
