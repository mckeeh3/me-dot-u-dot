# me-dot-u-dot

**me-dot-u-dot** is a research platform for exploring self-learning AI agent techniques through interactive gameplay. Agents begin with zero knowledge of game rules and progressively learn strategy, tactics, and decision-making through experience and structured memory systems.

## 🎯 Learning Objectives

This project demonstrates key techniques for implementing self-learning AI agents:

- **Zero-knowledge bootstrapping** - Agents discover game rules through trial and error
- **Experience-driven learning** - Each game builds upon previous knowledge
- **Structured memory systems** - Playbook-based long-term memory evolution
- **Multi-agent interactions** - Agents learn from human opponents and other agents
- **Guided self-improvement** - System prompts that shape learning quality and direction

## 🧠 Agent Architecture

### Agents and Agent Players

The game distinguishes between the **agent implementation** and the **agent players** who use it:

- Players are either humans or agents. Any game can be human vs human, human vs agent, or agent vs agent; the rules of play do not change.
- `DotGameAgent` represents the reusable agent capability. It encapsulates how an LLM session is invoked, which tools are available, and how tool responses are interpreted when a move is required.
- An **agent player** is a concrete player record (unique ID, display name, chosen LLM model). Each agent player owns persistent state:
  - a **playbook** (`PlaybookEntity`) that stores tactical instructions the model has authored, and
  - an **agent role/system prompt** (`AgentRoleEntity`) that frames long-term behavior and tool discipline.
  - both artifacts are unique per agent ID. Every agent player starts from the shared default system prompt and an empty playbook, but the prompt and playbook are expected to evolve as the model learns. Because the player
  record permanently captures the chosen LLM model, once an agent player is created its model cannot be changed.
- When a turn begins, `DotGameToAgentConsumer` launches `DotGameAgent` on behalf of the specific agent player. The agent player’s model must call the provided tools to read game state, consult both its
  playbook and system prompt, make a move, and optionally revise either artifact afterwards. The available tools are:
  - `GetGameStateTool`
  - `GetYourPlaybookTool`
  - `GetYourSystemPromptTool`
  - `MakeMoveTool`
  - `UpdateYourPlaybookTool`
  - `UpdateYourSystemPromptTool`
  - `GetGameMoveHistoryTool` (for post-game analysis prior to updating memories)
- Because playbook and role updates are scoped per agent ID, two agent players running on the same underlying LLM remain independent learners.
- Fetch the current playbook/system prompt before updating them, and always resubmit the full revised document when calling the update tools.

Agent players are also distinguished by their chosen LLM model. The player record captures both the agent type (always `DotGameAgent` today) and the specific model identifier (for example, `gpt-5-mini` versus `gemini-2.5-pro`). Running multiple agent players side by side lets you probe how different providers handle the same gameplay loop and self-learning workflow.

Observations so far include:

- **Response latency** – Even fast models often require several seconds, and sometimes minutes, to gather tool outputs and decide on a move.
- **Learning behavior** – Models vary in how aggressively they rewrite their playbooks and system prompts during and after each game, revealing different strategies for consolidating experience.
- **Operational cost** – Because every turn invokes the same tool sequence, cost differences between models become directly comparable while they execute identical tasks.

This separation—shared agent capabilities plus per-player memory and model choice—enables the core experiment of the app: agent players that continually adapt based on their own gameplay history while exposing how different LLMs behave under identical conditions.

### Learning Progression

Agents evolve through distinct phases:

1. **Random exploration** - Initial moves are essentially random as agents discover valid actions
2. **Pattern recognition** - Agents begin identifying successful and unsuccessful strategies
3. **Strategic development** - Sophisticated gameplay emerges from accumulated experience
4. **Adaptive optimization** - Agents refine tactics based on opponent behavior

### Memory & Learning Systems

- **Playbook** - Core long-term memory containing learned strategies and insights
- **Modifiable system prompt** - also, core long term memory that may be updated as decided by the model
- **Session memory** - Short-term context for current game decisions
- **Experience accumulation** - Each move and outcome contributes to the agent's knowledge base
- **Reflective learning** - Agents analyze their performance to improve future play

### System Prompt Engineering

The agent's system prompt is critical for guiding learning quality:

- Defines learning objectives and success metrics
- Establishes reasoning patterns and decision-making frameworks
- Shapes how agents interpret and store experiences
- Influences the quality and direction of self-improvement
- May be dynamically updated as part of the learning process

## 🏗️ Akka Integration

The application leverages Akka SDK components for scalable, event-driven architecture:

### Core Components

**Event Sourced Entities:**

- `DotGameEntity` - Manages game state, moves, and scoring through event sourcing
- `PlaybookEntity` - Stores the latest agent playbook instructions
- `PlaybookJournalEntity` - Tracks the evolution of agent playbooks over time
- `AgentRoleEntity` - Persists the agent system prompt / role definition
- `AgentRoleJournalEntity` - Archives historical agent role revisions
- `PlayerEntity` - Handles player profiles and statistics

**Akka Agents:**

- `DotGameAgent` - The core AI agent that makes game decisions and learns from experience
- Integrates with LLM providers
- Uses structured tools for game analysis, learning updates, and move selection

**Akka Agent Tools:**

- `GetGameStateTool` - Retrieves a compact view of the current board, players, scores, and move history
- `MakeMoveTool` - Submits the agent's selected move to the game entity after validation
- `GetYourPlaybookTool` - Returns the latest playbook instructions for the requesting agent
- `UpdateYourPlaybookTool` - Overwrites the agent's playbook with a revised instruction set
- `GetYourSystemPromptTool` - Provides the persisted system prompt when preparing an update
- `UpdateYourSystemPromptTool` - Persists a new system prompt / role definition for the agent
- `GetGameMoveHistoryTool` - Supplies enriched move timelines (including scoring sequences) for post-game analysis

**Views:**

- `DotGameView` - Queries for game states and history
- `PlaybookJournalView` - Provides access to agent learning progression
- `AgentRoleJournalView` - Exposes system prompt revisions for inspection
- `PlayerView` - Manages player data and statistics

**Consumers:**

- `DotGameToAgentConsumer` - Bridges game events to agent learning systems
- `PlaybookToPlaybookJournalConsumer` - Archives agent playbook evolution
- `AgentRoleToAgentRoleJournalConsumer` - Archives system prompt / role evolution

### Agent Learning Flow

1. `DotGameEntity` processes moves and emits game events
2. `DotGameToAgentConsumer` translates events into agent context
3. `DotGameAgent` reasons about the game state using mandatory tools
4. Agent updates its playbook or system prompt with new insights and strategies
5. `PlaybookJournalEntity` and `AgentRoleJournalEntity` record the learning progression for analysis

## 🔍 Observing Agent Learning

### Playbook Journal System

The playbook journal provides a unique window into agent cognition:

- **Not used by agents** - Pure observational tool for researchers/developers
- **Learning history** - Complete timeline of how each agent's strategy evolves
- **Decision archaeology** - Trace how specific insights developed over time
- **Comparative analysis** - Compare learning patterns between different agents

Access agent learning progression through the web interface's "View Journal" feature for any AI player.

### Agent Role Journal System

The agent role journal complements the playbook history by recording every version of the system prompt that governs an agent's behavior:

- **System prompt lineage** - Track how role guidance evolves alongside tactics
- **Diff-friendly** - The dedicated UI highlights additions/removals between revisions
- **Cross-reference** - Compare role changes with playbook updates to understand their combined effect on performance

View historical agent roles via `/agent-role.html`, which mirrors the playbook journal experience.

## 🛠️ Tech Stack

**Backend:**

- **Akka SDK** - Event sourcing, agents, views, and consumers
- **Java 21** - Modern language features and performance
- **Maven 3.9+** - Dependency management and build automation

**Frontend:**

- **Vanilla HTML/JavaScript** - Responsive web interface with real-time updates
- **CSS Grid & Flexbox** - Dynamic game board rendering
- **Server-Sent Events** - Real-time game state updates
- **RESTful API** - Clean communication with Akka backend

**AI/ML:**

- **LLM Integration** - OpenAI GPT-4o (pluggable architecture for other providers)
- **Structured Prompting** - Engineered system prompts for optimal learning
- **Tool Integration** - Agents use tools for game analysis, decision support, move execution, and maintaining playbooks/system prompts

## 🚀 Installation & Setup

### Prerequisites

- **Java 21** - Required for Akka SDK
- **Maven 3.9+** - Build and dependency management
- **Docker** - For local Akka runtime environment
- **API Keys for specific models as needed** - Or configure alternative LLM provider

### Configuration

1. Set your API keys in environment variables or application configuration
2. Configure model parameters in `src/main/resources/application.conf`
3. Adjust agent learning parameters as needed

### Running Locally

```bash
git clone https://github.com/your-org/me-dot-u-dot.git
cd me-dot-u-dot
./mvn compile exec:java
```

**Access the application:**

- **Web Interface**: [http://localhost:9000](http://localhost:9000)
- **API Endpoints**: Available at the same base URL

## 🎮 Usage

### Starting Games

1. **Create or select players** - Choose human players or AI agents
2. **Configure AI models** - Select LLM providers for agent players
3. **Begin gameplay** - Watch agents learn and adapt in real-time
4. **Observe learning** - Use "View Journal" to see agent reasoning evolution

### Game Modes

- **Human vs Agent** - Classic player vs AI learning experience
- **Agent vs Agent** - Observe how agents learn from each other
- **Multi-session learning** - Agents retain knowledge across games

### Monitoring Agent Progress

- Real-time move analysis and decision reasoning
- Playbook journal entries showing learning milestones
- Performance metrics and strategic development tracking

## 🔬 Research Applications

This platform enables research into:

- Self-supervised learning in game environments
- Memory architecture design for AI agents
- Multi-agent learning dynamics
- Prompt engineering for agent behavior shaping
- Long-term knowledge retention and application

---
*The game mechanics are intentionally undocumented - agents must discover the rules through play, just as the learning algorithms intended.*

## Codebase structure and key concepts description (Codex generated)

### Project overview

* **me-dot-u-dot** is an event-driven research platform where LLM-powered agents learn to play a grid-based game by iteratively updating their own playbooks and system prompts, letting you compare models and observe their learning trajectories.

### Repository layout

* Java sources follow a clean separation into `domain` models, `application` logic (entities, consumers, tools, views), and `api` HTTP endpoints, while static web assets and configuration live under `src/main/resources/static-resources` and `application.conf` respectively. Shell helpers in the repo root aid manual testing.

### Domain layer (game and memories)

* `DotGame` defines the authoritative game state, commands, events, scoring logic, and board representation used by the rest of the system.
* Player- and agent-specific memory is modeled separately: `Playbook` stores per-agent tactical instructions, `AgentRole` captures the editable system prompt, and both have corresponding journal records that archive every revision for later inspection.
* Additional domain models handle player identities (`Player`), aggregated player statistics (`PlayerGames`), and utility hashing (`Murmur1`).

### Application layer (entities, agent orchestration, tools)

* Event-sourced entities (`DotGameEntity`, `PlaybookEntity`, `AgentRoleEntity`, `PlaybookJournalEntity`, `AgentRoleJournalEntity`, `PlayerGamesEntity`) and a key-value `PlayerEntity` wrap the domain logic with Akka SDK persistence semantics.
* `DotGameAgent` is the Akka Agent that drives LLM interactions, wiring together the available tools, selecting a model from configuration, and handling recoverable vs. fatal errors during move execution.
* Consumers stream entity events into agent sessions or archival stores: `DotGameToAgentConsumer` orchestrates turn-by-turn prompting, while `PlaybookToPlaybookJournalConsumer` and `AgentRoleToAgentRoleJournalConsumer` record every playbook/system prompt change with ordered sequence IDs; a `SessionMemoryConsumer` logs Akka session-memory events.
* Tool classes (`GetGameStateTool`, `MakeMoveTool`, `GetYourPlaybookTool`, `UpdateYourPlaybookTool`, `GetYourSystemPromptTool`, `UpdateYourSystemPromptTool`, `GetGameMoveHistoryTool`) expose structured, documented capabilities the LLM must call each turn.

### Query views and analytics

* Read models (`DotGameView`, `PlaybookJournalView`, `AgentRoleJournalView`, `PlayerView`, `PlayerGamesView`) project event streams into SQL-addressable tables for move streams, journal navigation, player listings, and leader-board statistics.

### HTTP API surface

* REST endpoints under `/game`, `/player`, `/playbook`, `/agent-role`, and `/player-games` expose commands and queries for gameplay, player management, journals, and leader boards, while `StaticContentEndpoint` serves the SPA assets.

### Frontend experience

* The main game UI (`index.html` + `js/index.js`) guides players through setup, renders the board, streams moves via SSE, and coordinates timers and sounds using utilities in `js/common.js`. Supporting pages (`playbook.html`, `agent-role.html`, `leader-board.html`, etc.) reuse shared components to browse journals and standings.

### Configuration & build

* Runtime wiring lives in `application.conf`, which documents the naming convention for agent models and ships reference configs for OpenAI, Google Gemini, Anthropic, and local Ollama providers; API keys are expected via environment variables.
* Maven coordinates in `pom.xml` inherit from the Akka Java SDK parent, and README outlines local run instructions with `mvn compile exec:java` and the necessary toolchain versions.

### Testing & developer tooling

* A JUnit-based integration test scaffold (`IntegrationTest`) is ready for end-to-end scenarios, and repository guidelines emphasize running `mvn test`/`mvn clean verify` plus adhering to the provided style conventions. Command-line scripts (`cancel-game.sh`, `agent-role-reset.sh`, `get-games-by-player.sh`, `leader-board.sh`) offer quick API probes during development.

### Suggested next steps for newcomers

* **Trace an agent turn**: Follow a `DotGame.Event.MoveMade` through `DotGameToAgentConsumer` into `DotGameAgent.makeMove`, inspecting how tool calls and retries work before diving into model configuration.
* **Instrument the journals**: Explore `PlaybookJournalView` and `AgentRoleJournalView` queries, then open the corresponding HTML/JS pages to understand how the UI paginates through revisions.
* **Enhance test coverage**: Use the integration test skeleton to script realistic game flows, leveraging the shell helpers to mirror HTTP traffic while you build assertions around the Akka entities and views.
* **Review configuration management**: Familiarize yourself with the agent model naming conventions and environment variable expectations in `application.conf` before adding new providers or tuning parameters.
* **Study frontend orchestration**: Read through `js/index.js` and `js/common.js` to see how the SPA consumes REST/SSE endpoints; this context is invaluable when adding new controls or diagnostics to the dashboard.

By iterating through these areas you'll quickly gain confidence in how the Akka backend, LLM agents, and frontend experience collaborate to support self-improving gameplay.
