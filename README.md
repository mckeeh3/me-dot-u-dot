# me-dot-u-dot

**me-dot-u-dot** is a visual, interactive game designed to demonstrate how AI agents learn over time through structured context, reasoning loops, tool use, and memory. The game mechanics are simple—but the real action happens inside the agent's evolving mind.

## 🎯 Purpose

To showcase the internal workings of AI agents using:

- The **ReAct loop** (Reasoning + Acting)
- The **Learning loop** (Experience accumulation via memory)
- **Structured context** as the agent's only window into the world

This project is built to help developers *see* how agents think, learn, and evolve.

---

## 🎮 Game Overview

- 5×5 grid
- Two players: **You** and **the Agent**
- Take turns placing colored dots
- Score 1 point for each straight line of 3 of your own dots
- You can **overwrite your own dots**, but not your opponent's
- First to 3 points wins

---

## 🧠 How the Agent Works

On each turn, the agent:

1. Receives a **structured context** (board state, past moves, system prompt, memory)
2. **Reasons** using a language model
3. Optionally **invokes tools** (e.g. check for win state, valid moves)
4. Chooses a move
5. Adds that experience to its **session memory**
6. Optionally **reflects** or **summarizes** to improve future performance

You can watch the context evolve in real-time.

---

## 🛠️ Tech Stack

**Backend:**

- **Akka SDK** (Agents, Workflows, Event Sourced Entities)
- **Java 21**
- **Maven 3.9+**

**Frontend:**

- **Vanilla HTML/JavaScript** - Simple, responsive web interface
- **Separated JS** - Game logic in `index.js` for maintainability
- **CSS Grid** - Clean 5×5 game board layout
- **RESTful API** - Communication with Akka backend

**AI/ML:**

- **LLM**: OpenAI GPT-4o by default (pluggable)
- **Simple Agent** - Random cell selection (Phase 1)

---

## 🚀 Getting Started

### Prerequisites

- Java 21
- Maven 3.9+
- Docker (for local Akka runtime)
- OpenAI API key (or plug in another model provider)

### Run Locally

```bash
git clone https://github.com/your-org/me-dot-u-dot.git
cd me-dot-u-dot
./mvnw compile exec:java
```

**Access the application:**

- Backend API: [localhost:9000](http://localhost:9000)
- Frontend UI: [localhost:9000](http://localhost:9000) (served by Akka)

### API Endpoints

- `POST /api/game/move` - Make a game move and get AI response
- `GET /` - Web UI interface
