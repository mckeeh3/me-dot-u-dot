// Leader Board Page JavaScript

// State management
let leaderBoardData = [];
let selectedPlayerId = null;
let selectedGameId = null;
let playerGamesData = [];

// Initialize the page
document.addEventListener('DOMContentLoaded', async () => {
  await loadLeaderBoard();
});

// Load leader board data
async function loadLeaderBoard() {
  try {
    const response = await fetch('/player-games/get-leader-board', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify({ limit: 50, offset: 0 }),
    });

    if (response.ok) {
      const data = await response.json();
      leaderBoardData = data.playerGames || [];
      renderLeaderBoard();

      // Auto-select top player and load their games
      if (leaderBoardData.length > 0) {
        selectPlayer(leaderBoardData[0].playerId);
      }
    } else {
      console.error('Failed to load leader board');
    }
  } catch (error) {
    console.error('Error loading leader board:', error);
  }
}

// Render leader board table
function renderLeaderBoard() {
  const tbody = document.getElementById('leaderBoardBody');
  tbody.innerHTML = '';

  leaderBoardData.forEach((player, index) => {
    const row = document.createElement('tr');
    row.dataset.playerId = player.playerId;
    row.onclick = () => selectPlayer(player.playerId);

    row.innerHTML = `
            <td>${player.playerId}</td>
            <td>${player.gamesWon || 0}</td>
        `;

    tbody.appendChild(row);
  });
}

// Select a player and load their games
async function selectPlayer(playerId) {
  selectedPlayerId = playerId;
  selectedGameId = null;

  // Update UI to show selected player
  updateSelectedPlayerUI();

  // Load player's games
  await loadPlayerGames(playerId);
}

// Update UI to show selected player
function updateSelectedPlayerUI() {
  // Clear previous selections
  document.querySelectorAll('.leader-table tbody tr').forEach((row) => {
    row.classList.remove('selected');
  });

  // Highlight selected player
  const selectedRow = document.querySelector(`.leader-table tbody tr[data-player-id="${selectedPlayerId}"]`);
  if (selectedRow) {
    selectedRow.classList.add('selected');
  }

  // Update section 2 title
  document.getElementById('selectedPlayerId').textContent = selectedPlayerId;
}

// Load games for a specific player
async function loadPlayerGames(playerId) {
  try {
    const response = await fetch('/game/get-games-by-player-id-paged', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify({ playerId, limit: 50, offset: 0 }),
    });

    if (response.ok) {
      const data = await response.json();
      playerGamesData = data.games || [];
      renderPlayerGames();

      // Auto-select the last game played
      if (playerGamesData.length > 0) {
        selectGame(playerGamesData[0].gameId);
      }
    } else {
      console.error('Failed to load player games');
    }
  } catch (error) {
    console.error('Error loading player games:', error);
  }
}

// Render player games table
function renderPlayerGames() {
  const tbody = document.getElementById('playerGamesBody');
  tbody.innerHTML = '';

  playerGamesData.forEach((game, index) => {
    const row = document.createElement('tr');
    row.dataset.gameId = game.gameId;
    row.onclick = () => selectGame(game.gameId);

    const createdTime = formatDateTime(game.createdAt);
    const status = getGameStatus(game);

    row.innerHTML = `
            <td>${createdTime}</td>
            <td>${status}</td>
        `;

    tbody.appendChild(row);
  });
}

// Get game status display text
function getGameStatus(game) {
  if (game.status === 'won_by_player') {
    const winnerId = game.player1Score > game.player2Score ? game.player1Id : game.player2Id;
    return winnerId === selectedPlayerId ? '🏆 Win' : '❌ Loss';
  } else if (game.status === 'draw') {
    return '🤝 Draw';
  } else if (game.status === 'canceled') {
    return '🚫 Canceled';
  } else {
    return '⏳ In Progress';
  }
}

// Select a game and load its details
async function selectGame(gameId) {
  selectedGameId = gameId;

  // Update UI to show selected game
  updateSelectedGameUI();

  // Load game details
  await loadGameDetails(gameId);
}

// Update UI to show selected game
function updateSelectedGameUI() {
  // Clear previous selections
  document.querySelectorAll('.games-table tbody tr').forEach((row) => {
    row.classList.remove('selected');
  });

  // Highlight selected game
  const selectedRow = document.querySelector(`.games-table tbody tr[data-game-id="${selectedGameId}"]`);
  if (selectedRow) {
    selectedRow.classList.add('selected');
  }
}

// Load game details and render game board
async function loadGameDetails(gameId) {
  try {
    const response = await fetch(`/game/get-state/${gameId}`);

    if (response.ok) {
      const { gameState } = await response.json();
      renderGameInfo(gameState);
      renderGameBoard(gameState);
    } else {
      console.error('Failed to load game details');
    }
  } catch (error) {
    console.error('Error loading game details:', error);
  }
}

// Render game information
function renderGameInfo(gameState) {
  const gameInfo = document.getElementById('gameInfo');

  const p1 = gameState.player1Status;
  const p2 = gameState.player2Status;
  const gameDuration = calculateGameDuration(gameState.createdAt, gameState.finishedAt);

  gameInfo.innerHTML = `
        <div class="game-summary">
            <h3>Game: ${gameState.gameId}</h3>
            <div class="player-info">
                <div class="player player1-bg">
                    <span class="player-avatar">${p1.player.type === 'agent' ? '🤖' : '👤'}</span>
                    <span class="player1-name">${p1.player.name}</span>
                    <span class="player-score">Score: ${p1.score}</span>
                    <span class="player-moves">Moves: ${p1.moves}</span>
                </div>
                <div class="player player2-bg">
                    <span class="player-avatar">${p2.player.type === 'agent' ? '🤖' : '👤'}</span>
                    <span class="player2-name">${p2.player.name}</span>
                    <span class="player-score">Score: ${p2.score}</span>
                    <span class="player-moves">Moves: ${p2.moves}</span>
                </div>
            </div>
            <div class="game-meta">
                <span class="game-status">Status: ${getGameStatusDisplay(gameState.status)}</span>
                <span class="game-duration">Duration: ${gameDuration}</span>
                <span class="game-level">Level: ${gameState.board.level}</span>
            </div>
        </div>
    `;
}

// Get game status display
function getGameStatusDisplay(status) {
  switch (status) {
    case 'in_progress':
      return '🔄 In Progress';
    case 'won_by_player':
      return '🏆 Game Over';
    case 'draw':
      return '🤝 Draw';
    case 'canceled':
      return '🚫 Canceled';
    default:
      return status;
  }
}

// Calculate game duration
function calculateGameDuration(createdAt, finishedAt) {
  if (!createdAt || !finishedAt) return 'Unknown';

  const start = new Date(createdAt);
  const end = new Date(finishedAt);
  const durationMs = end.getTime() - start.getTime();

  const minutes = Math.floor(durationMs / 60000);
  const seconds = Math.floor((durationMs % 60000) / 1000);

  return `${minutes}m ${seconds}s`;
}

// Render game board
function renderGameBoard(gameState) {
  const board = document.getElementById('gameBoard');
  board.innerHTML = '';

  const levelSizeMap = { one: 5, two: 7, three: 9, four: 11, five: 13, six: 15, seven: 17, eight: 19, nine: 21 };
  const size = levelSizeMap[gameState.board.level] || 5;

  board.style.setProperty('--size', size);
  board.style.gridTemplateColumns = `repeat(${size}, 1fr)`;

  const dots = gameState.board.dots || [];
  const byId = new Map(dots.map((d) => [d.id, d]));
  const lastMoveId = gameState.moveHistory?.length ? gameState.moveHistory[gameState.moveHistory.length - 1].dotId : null;

  for (let r = 0; r < size; r++) {
    for (let c = 0; c < size; c++) {
      const rowChar = String.fromCharCode('A'.charCodeAt(0) + r);
      const id = rowChar + (c + 1);
      const dot = byId.get(id);

      const cell = document.createElement('div');
      cell.className = 'cell';
      cell.dataset.dotId = id;

      if (dot && dot.player && dot.player.id) {
        const playerId = dot.player.id;
        const isPlayer1 = playerId === gameState.player1Status.player.id;
        const cls = isPlayer1 ? 'player' : 'ai';
        cell.classList.add(cls);
        cell.textContent = '●';
      }

      if (lastMoveId && id === lastMoveId) {
        cell.classList.add('last-move');
      }

      board.appendChild(cell);
    }
  }
}
