let gameState = initializeGameState();
let gameId = generateGameId();
let isPlayerTurn = true;
let playerScore = 0;
let aiScore = 0;

function initializeGameState() {
  // Create 5x5 grid with all cells empty (0)
  let state = [];
  for (let i = 0; i < 5; i++) {
    let row = [];
    for (let j = 0; j < 5; j++) {
      row.push(0);
    }
    state.push(row);
  }
  return state;
}

function generateGameId() {
  return 'game-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9);
}

function renderBoard() {
  const board = document.getElementById('gameBoard');
  board.innerHTML = '';

  for (let i = 0; i < 5; i++) {
    for (let j = 0; j < 5; j++) {
      const cell = document.createElement('div');
      cell.className = 'cell';
      cell.dataset.row = i;
      cell.dataset.col = j;

      if (gameState[i][j] === 1) {
        cell.classList.add('player');
        cell.textContent = '●';
      } else if (gameState[i][j] === 2) {
        cell.classList.add('ai');
        cell.textContent = '●';
      }

      if (gameState[i][j] === 0 && isPlayerTurn) {
        cell.addEventListener('click', () => makeMove(i, j));
      }

      board.appendChild(cell);
    }
  }
}

function updateStatus(message, isPlayerTurn) {
  const status = document.getElementById('status');
  status.textContent = message;
  status.className = 'status ' + (isPlayerTurn ? 'player-turn' : 'ai-turn');
}

function updateScore() {
  document.getElementById('playerScore').textContent = playerScore;
  document.getElementById('aiScore').textContent = aiScore;
}

function gameStateToString() {
  return gameState.map((row) => row.join(',')).join('|');
}

function stringToGameState(stateStr) {
  const rows = stateStr.split('|');
  return rows.map((row) => row.split(',').map((cell) => parseInt(cell)));
}

async function makeMove(row, col) {
  if (!isPlayerTurn || gameState[row][col] !== 0) {
    return;
  }

  isPlayerTurn = false;
  updateStatus('AI is thinking...', false);

  try {
    const response = await fetch('/api/game/move', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        gameId: gameId,
        row: row,
        col: col,
        gameState: gameStateToString(),
      }),
    });

    const result = await response.json();

    // Update game state with both player's and AI's moves
    gameState = stringToGameState(result.gameState);

    // Set player turn to true before rendering board
    isPlayerTurn = true;
    renderBoard();

    updateStatus(result.message + ' - Your turn!', true);

    // Check for wins
    checkForWins();
  } catch (error) {
    console.error('Error making move:', error);
    updateStatus('Error making move. Please try again.', true);
    isPlayerTurn = true;
  }
}

function checkForWins() {
  // Simple win detection - check for 3 in a row
  // This is a basic implementation - you can enhance it later
  let playerWins = 0;
  let aiWins = 0;

  // Check rows, columns, and diagonals for 3 in a row
  // (Simplified for now - you can add more sophisticated win detection)

  if (playerWins > 0) {
    playerScore++;
    updateScore();
    updateStatus('You won this round!', true);
  } else if (aiWins > 0) {
    aiScore++;
    updateScore();
    updateStatus('AI won this round!', true);
  }
}

function newGame() {
  gameState = initializeGameState();
  gameId = generateGameId();
  isPlayerTurn = true;
  updateStatus('New game! Your turn!', true);
  renderBoard();
}

function resetGame() {
  gameState = initializeGameState();
  gameId = generateGameId();
  isPlayerTurn = true;
  playerScore = 0;
  aiScore = 0;
  updateScore();
  updateStatus('Game reset! Your turn!', true);
  renderBoard();
}

// Initialize the game when the page loads
document.addEventListener('DOMContentLoaded', function () {
  renderBoard();
  updateStatus('Your turn! Click a cell to place your dot.', true);
});
