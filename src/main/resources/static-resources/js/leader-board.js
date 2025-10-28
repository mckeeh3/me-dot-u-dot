// Leader Board Page JavaScript

// State management
let leaderBoardData = [];
let selectedPlayerId = null;
let selectedGameId = null;
const urlParams = new URLSearchParams(window.location.search);
const initialPlayerId = urlParams.get('playerId');
const initialGameId = urlParams.get('gameId');
let initialSelectionApplied = false;
let playerGamesData = [];
let replayState = {
  gameState: null,
  moveHistory: [],
  index: 0,
  players: {},
};

let lastReplaySnapshot = null;
let gameInfoVisible = true;

// Initialize the page
document.addEventListener('DOMContentLoaded', async () => {
  setupReplayControls();
  ensureReplayBoardSizing($('gameBoard'));
  window.addEventListener('resize', handleReplayResize, { passive: true });
  initGameInfoToggle();
  await loadLeaderBoard();
  document.addEventListener('keydown', handleReplayKeyboard, { passive: false });
});

document.addEventListener('DOMContentLoaded', () => {
  const refreshBtn = $('leaderRefreshBtn');
  if (refreshBtn) {
    refreshBtn.addEventListener('click', () => {
      loadLeaderBoard();
    });
  }
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

      // Auto-select preferred player and game
      if (leaderBoardData.length > 0) {
        if (!initialSelectionApplied) {
          let preferredPlayerId = leaderBoardData[0].playerId;
          if (initialPlayerId && leaderBoardData.some((player) => player.playerId === initialPlayerId)) {
            preferredPlayerId = initialPlayerId;
          }
          initialSelectionApplied = true;
          await selectPlayer(preferredPlayerId, { preferredGameId: initialGameId });
        } else if (!selectedPlayerId) {
          await selectPlayer(leaderBoardData[0].playerId);
        }
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
  const tbody = $('leaderBoardBody');
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

function setupReplayControls() {
  const firstBtn = $('replayFirstBtn');
  const prevBtn = $('replayPrevBtn');
  const nextBtn = $('replayNextBtn');
  const lastBtn = $('replayLastBtn');

  if (firstBtn) {
    firstBtn.addEventListener('click', () => setReplayIndex(0));
  }
  if (prevBtn) {
    prevBtn.addEventListener('click', () => setReplayIndex(replayState.index - 1));
  }
  if (nextBtn) {
    nextBtn.addEventListener('click', () => setReplayIndex(replayState.index + 1));
  }
  if (lastBtn) {
    lastBtn.addEventListener('click', () => setReplayIndex(replayState.moveHistory.length));
  }

  updateReplayUI();
}

function initGameInfoToggle() {
  const toggleBtn = $('gameInfoToggleBtn');
  if (!toggleBtn) {
    return;
  }

  toggleBtn.addEventListener('click', () => {
    gameInfoVisible = !gameInfoVisible;
    applyGameInfoVisibility();
  });

  applyGameInfoVisibility();
}

function applyGameInfoVisibility() {
  const gameInfo = $('gameInfo');
  const toggleBtn = $('gameInfoToggleBtn');

  if (gameInfo) {
    if (gameInfoVisible) {
      gameInfo.removeAttribute('hidden');
    } else {
      gameInfo.setAttribute('hidden', '');
    }
    gameInfo.classList.toggle('is-hidden', !gameInfoVisible);
  }

  if (toggleBtn) {
    toggleBtn.setAttribute('aria-expanded', String(gameInfoVisible));
    toggleBtn.setAttribute('title', gameInfoVisible ? 'Hide game details' : 'Show game details');
    toggleBtn.classList.toggle('is-collapsed', !gameInfoVisible);

    const label = toggleBtn.querySelector('.toggle-label');
    if (label) {
      label.textContent = gameInfoVisible ? 'Hide details' : 'Show details';
    }

    const icon = toggleBtn.querySelector('.icon-symbol');
    if (icon) {
      icon.textContent = gameInfoVisible ? '👁' : '🙈';
    }
  }

  if (replayState?.index) {
    renderGameBoardAtIndex(buildReplaySnapshot(replayState.index));
  }
}

// Select a player and load their games
async function selectPlayer(playerId, options = {}) {
  selectedPlayerId = playerId;
  selectedGameId = null;

  // Update UI to show selected player
  updateSelectedPlayerUI();

  // Load player's games
  await loadPlayerGames(playerId, options);
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
  $('selectedPlayerId').textContent = selectedPlayerId;
}

// Load games for a specific player
async function loadPlayerGames(playerId, options = {}) {
  const { preferredGameId = null } = options;
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

      if (playerGamesData.length > 0) {
        let desiredGameId = null;
        if (preferredGameId && playerGamesData.some((game) => game.gameId === preferredGameId)) {
          desiredGameId = preferredGameId;
        }
        if (!desiredGameId) {
          desiredGameId = playerGamesData[0].gameId;
        }
        selectGame(desiredGameId);
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
  const tbody = $('playerGamesBody');
  tbody.innerHTML = '';

  playerGamesData.forEach((game, index) => {
    const row = document.createElement('tr');
    row.dataset.gameId = game.gameId;
    row.onclick = () => selectGame(game.gameId);

    const createdTime = formatDateTime(game.createdAt);
    const status = getGameStatus(game);

    const createdCell = document.createElement('td');
    createdCell.textContent = createdTime;

    const statusCell = document.createElement('td');
    statusCell.classList.add('status-cell');
    const statusText = document.createElement('span');
    statusText.textContent = status;
    statusCell.appendChild(statusText);
    const statusLink = document.createElement('a');
    statusLink.href = `/game-action-log.html?gameId=${encodeURIComponent(game.gameId)}&playerId=${encodeURIComponent(selectedPlayerId)}`;
    statusLink.textContent = '📜';
    statusLink.classList.add('game-log-link');
    statusLink.setAttribute('aria-label', 'View game action log');
    statusLink.title = 'View logs';
    statusLink.addEventListener('click', (event) => {
      event.stopPropagation();
    });
    statusCell.appendChild(statusLink);

    row.appendChild(createdCell);
    row.appendChild(statusCell);

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
    // const [stateResponse, historyResponse] = await Promise.all([fetch(`/game/get-state/${gameId}`), fetch(`/game/get-game-move-history-tool/${gameId}`)]);
    const [stateResponse, historyResponse] = await Promise.all([fetch(`/game/get-state/${gameId}`), fetch(`/game/get-game-move-history/${gameId}`)]);

    if (!stateResponse.ok || !historyResponse.ok) {
      console.error('Failed to load game details');
      return;
    }

    const statePayload = await stateResponse.json();
    const historyPayload = await historyResponse.json();

    const gameState = statePayload.gameState;
    const moveHistory = historyPayload.moves || [];

    replayState = {
      gameState,
      moveHistory,
      index: moveHistory.length,
      players: buildPlayerMeta(gameState),
    };

    updateReplayUI();
  } catch (error) {
    console.error('Error loading game details:', error);
  }
}

// Render game information
function renderGameInfo(gameState, perPlayerStats = {}) {
  const gameInfo = $('gameInfo');
  const headerId = $('gameDetailsId');
  if (headerId) {
    headerId.textContent = gameState.gameId;
  }

  const p1 = gameState.player1Status;
  const p2 = gameState.player2Status;
  const gameDuration = calculateGameDuration(gameState.createdAt, gameState.finishedAt);

  // Determine the winner
  let p1IsWinner = false;
  let p2IsWinner = false;

  if (gameState.status === 'won_by_player') {
    p1IsWinner = p1.score > p2.score;
    p2IsWinner = p2.score > p1.score;
  }

  const p1ReplayStats = perPlayerStats[p1.player.id] || { score: p1.score, moves: p1.moves };
  const p2ReplayStats = perPlayerStats[p2.player.id] || { score: p2.score, moves: p2.moves };

  gameInfo.innerHTML = `
        <div class="game-summary">
            <div class="player-info">
                <div class="player player1-bg">
                    <span class="player-avatar">${p1.player.type === 'agent' ? '🤖' : '👤'}</span>
                    <span class="player1-name">${p1IsWinner ? '🏆 ' : ''}${p1.player.name}</span>
                    <span class="player-score">Score: ${p1ReplayStats.score}</span>
                    <span class="player-moves">Moves: ${p1ReplayStats.moves}</span>
                </div>
                <div class="player player2-bg">
                    <span class="player-avatar">${p2.player.type === 'agent' ? '🤖' : '👤'}</span>
                    <span class="player2-name">${p2IsWinner ? '🏆 ' : ''}${p2.player.name}</span>
                    <span class="player-score">Score: ${p2ReplayStats.score}</span>
                    <span class="player-moves">Moves: ${p2ReplayStats.moves}</span>
                </div>
            </div>
            <div class="game-meta">
                <span class="game-status">Status: ${getGameStatusDisplay(gameState.status)}</span>
                <span class="game-duration">Duration: ${gameDuration}</span>
                <span class="game-level">Level: ${gameState.board.level}</span>
            </div>
        </div>
    `;

  applyGameInfoVisibility();
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

function calculateGameDuration(createdAt, finishedAt) {
  if (!createdAt || !finishedAt) return 'Unknown';

  const start = new Date(createdAt);
  const end = new Date(finishedAt);
  const durationMs = end.getTime() - start.getTime();

  const minutes = Math.floor(durationMs / 60000);
  const seconds = Math.floor((durationMs % 60000) / 1000);

  return `${minutes}m ${seconds}s`;
}

function calculateMoveThinkTime(thinkMs) {
  if (!thinkMs) return '';
  const minutes = Math.floor(thinkMs / 60000);
  const seconds = Math.floor((thinkMs % 60000) / 1000);
  return minutes > 0 ? `${minutes}m${seconds}s` : `${seconds}s`;
}

function buildPlayerMeta(gameState) {
  const p1 = gameState.player1Status.player;
  const p2 = gameState.player2Status.player;

  return {
    [p1.id]: {
      name: p1.name,
      type: p1.type,
      cssClass: 'player1',
    },
    [p2.id]: {
      name: p2.name,
      type: p2.type,
      cssClass: 'player2',
    },
  };
}

function setReplayIndex(index) {
  if (!replayState.gameState) {
    return;
  }

  const total = replayState.moveHistory.length;
  const clamped = Math.max(0, Math.min(index, total));
  replayState.index = clamped;
  updateReplayUI();
}

function updateReplayUI() {
  const snapshot = buildReplaySnapshot(replayState.index);
  lastReplaySnapshot = snapshot;
  updateReplayButtons(snapshot);
  updateReplayStatus(snapshot);
  if (replayState.gameState) {
    renderGameInfo(replayState.gameState, snapshot.perPlayer);
  }
  renderMoveDetails(snapshot);
  renderGameBoardAtIndex(snapshot);
}

function buildReplaySnapshot(index) {
  if (!replayState.gameState) {
    return {
      totalMoves: 0,
      index: 0,
      occupancy: new Map(),
      perPlayer: {},
      currentMove: null,
      scoringSquares: [],
    };
  }

  const totalMoves = replayState.moveHistory.length;
  const clamped = Math.max(0, Math.min(index, totalMoves));
  const occupancy = new Map();
  const perPlayer = {};

  Object.keys(replayState.players).forEach((playerId) => {
    perPlayer[playerId] = { moves: 0, score: 0 };
  });

  let currentMove = null;
  let scoringSquares = [];

  for (let i = 0; i < clamped; i++) {
    const move = replayState.moveHistory[i];
    const playerStats = perPlayer[move.playerId] || { moves: 0, score: 0 };

    playerStats.moves += 1;
    playerStats.score += move.moveScore || 0;
    perPlayer[move.playerId] = playerStats;

    occupancy.set(move.squareId, {
      playerId: move.playerId,
      gameMove: i + 1,
      playerMove: playerStats.moves,
      thinkTime: calculateMoveThinkTime(move.thinkMs),
      moveScore: move.moveScore || 0,
    });

    if (i === clamped - 1) {
      currentMove = move;
      const ids = (move.scoringMoves || []).flatMap((sm) => sm.scoringSquareIds || []);
      scoringSquares = ids.includes(move.squareId) ? ids : [move.squareId, ...ids];
    }
  }

  return {
    totalMoves,
    index: clamped,
    occupancy,
    perPlayer,
    currentMove,
    scoringSquares,
  };
}

function updateReplayButtons(snapshot) {
  const total = snapshot.totalMoves;
  const index = snapshot.index;
  const disabled = total === 0;

  const firstBtn = $('replayFirstBtn');
  const prevBtn = $('replayPrevBtn');
  const nextBtn = $('replayNextBtn');
  const lastBtn = $('replayLastBtn');

  if (firstBtn) firstBtn.disabled = disabled || index === 0;
  if (prevBtn) prevBtn.disabled = disabled || index === 0;
  if (nextBtn) nextBtn.disabled = disabled || index === total;
  if (lastBtn) lastBtn.disabled = disabled || index === total;
}

function updateReplayStatus(snapshot) {
  const statusEl = $('replayStatus');
  if (!statusEl) return;

  const total = snapshot.totalMoves;
  const index = snapshot.index;

  if (total === 0) {
    statusEl.textContent = replayState.gameState ? 'No moves recorded' : 'Move 0 / 0';
    return;
  }

  if (index === 0) {
    statusEl.textContent = `Move 0 / ${total} (start)`;
    return;
  }

  const move = snapshot.currentMove;
  const playerMeta = move ? replayState.players[move.playerId] : null;
  const playerLabel = playerMeta ? playerMeta.name : move?.playerId || '';
  statusEl.textContent = `Move ${index} / ${total} – ${playerLabel} (${move?.squareId || ''})`;
}

function renderGameBoardAtIndex(snapshot) {
  const boardEl = $('gameBoard');
  if (!boardEl) return;

  const metrics = ensureReplayBoardSizing(boardEl);
  boardEl.innerHTML = '';

  if (!replayState.gameState || !metrics || metrics.boardSizePx <= 0) {
    return;
  }

  const levelSizeMap = { one: 5, two: 7, three: 9, four: 11, five: 13, six: 15, seven: 17, eight: 19, nine: 21 };
  const size = levelSizeMap[replayState.gameState.board.level] || 5;

  boardEl.style.setProperty('--size', size);
  boardEl.style.gridTemplateColumns = `repeat(${size}, 1fr)`;
  const boardStyles = window.getComputedStyle(boardEl);
  const gapPx = parseFloat(boardStyles.columnGap || boardStyles.gap || 0) || 0;
  const totalGap = gapPx * Math.max(0, size - 1);
  const effectiveSpan = Math.max(metrics.boardSizePx - totalGap, 0);
  const squarePx = size > 0 ? effectiveSpan / size : 0;
  const baseFontPx = Math.max(squarePx * 0.12, 10);
  boardEl.style.fontSize = `${baseFontPx}px`;

  const scoringSquares = new Set(snapshot.scoringSquares || []);

  for (let r = 0; r < size; r++) {
    for (let c = 0; c < size; c++) {
      const rowChar = String.fromCharCode('A'.charCodeAt(0) + r);
      const squareId = rowChar + (c + 1);

      const squareEl = document.createElement('div');
      squareEl.className = 'square';
      squareEl.dataset.squareId = squareId;

      const moveData = snapshot.occupancy.get(squareId);

      if (moveData) {
        const playerMeta = replayState.players[moveData.playerId];
        if (playerMeta?.cssClass) {
          squareEl.classList.add(playerMeta.cssClass);
        }

        squareEl.innerHTML = `
          <div class="square-layer square-layer-top">
            <span class="square-id">${squareId}</span>
            <span class="game-move-count">${moveData.gameMove}</span>
          </div>
          <div class="square-layer square-layer-middle">
            <span class="player-square">●</span>
          </div>
          <div class="square-layer square-layer-bottom">
            <span class="player-think-time">${moveData.thinkTime || ''}</span>
            <span class="player-move-count">${moveData.playerMove}</span>
          </div>
        `;
      } else {
        squareEl.innerHTML = `
          <div class="square-layer square-layer-top">
            <span class="square-id"></span>
            <span class="game-move-count"></span>
          </div>
          <div class="square-layer square-layer-middle">
            <span class="player-square"></span>
          </div>
          <div class="square-layer square-layer-bottom">
            <span class="player-think-time"></span>
            <span class="player-move-count"></span>
          </div>
        `;
      }

      if (snapshot.currentMove && snapshot.currentMove.squareId === squareId) {
        squareEl.classList.add('last-move');
      }

      if (scoringSquares.has(squareId)) {
        squareEl.classList.add('scoring-square');
      }

      boardEl.appendChild(squareEl);

      if (moveData) {
        const squareData = SquarePopup.squareDataFromReplayState(squareEl, replayState, moveData);
        SquarePopup.squarePopupSetup(squareEl, squareData);
      }
    }
  }
}

function renderMoveDetails(snapshot) {
  const detailsEl = $('moveDetails');
  if (!detailsEl) return;

  if (!replayState.gameState) {
    detailsEl.textContent = 'Select a game to review its moves.';
    return;
  }

  if (snapshot.totalMoves === 0) {
    detailsEl.textContent = 'No moves were recorded for this game.';
    return;
  }

  if (snapshot.index === 0) {
    detailsEl.textContent = 'Game start. Use the controls above to step through each move.';
    return;
  }

  const move = snapshot.currentMove;
  const playerMeta = replayState.players[move.playerId];
  const playerName = playerMeta ? playerMeta.name : move.playerId;
  const moveScore = move.moveScore || 0;
  const thinkDisplay = calculateMoveThinkTime(move.thinkMs);
  const scoringMoves = move.scoringMoves || [];
  const scoringMoveTypePrefix = (type) => {
    if (!type) return '';
    switch (type) {
      case 'horizontal':
        return 'H';
      case 'vertical':
        return 'V';
      case 'diagonal':
        return 'D';
      case 'adjacent':
        return 'A';
      case 'topToBottom':
        return 'T2B';
      case 'leftToRight':
        return 'L2R';
      default:
        return type.charAt(0).toUpperCase();
    }
  };

  const formatScoringMove = (scoringMove) => {
    const squares = (scoringMove.scoringSquareIds || []).join(', ');
    const prefix = scoringMoveTypePrefix(scoringMove.type);
    const score = scoringMove.score;

    let typeAndSquares = squares;
    if (prefix && squares) {
      typeAndSquares = `${prefix}-${squares}`;
    } else if (prefix) {
      typeAndSquares = prefix;
    }

    const scorePart = score === undefined || score === null ? '' : `${score}`;
    if (!scorePart) {
      return typeAndSquares;
    }

    return typeAndSquares ? `${scorePart}|${typeAndSquares}` : scorePart;
  };

  let scoringText = 'None';
  if (scoringMoves.length > 0) {
    const formattedMoves = scoringMoves.map(formatScoringMove);
    if (formattedMoves.length === 1) {
      scoringText = formattedMoves[0];
    } else {
      scoringText = formattedMoves.map((moveText) => `(${moveText})`).join(', ');
    }
  }

  detailsEl.innerHTML = `
    <div><strong>Move ${snapshot.index}:</strong> ${playerName} played <strong>${move.squareId}</strong> ${thinkDisplay ? `after ${thinkDisplay}` : ''}.</div>
    <div>Points this move: <strong>${moveScore}</strong> • Scoring squares: <strong>${scoringText}</strong>.</div>
  `;
}

function ensureReplayBoardSizing(boardEl) {
  if (!boardEl) return null;
  const container = boardEl.parentElement;
  if (!container) return null;

  const containerStyles = window.getComputedStyle(container);
  const gapValue = parseFloat(containerStyles.rowGap || containerStyles.gap || 0) || 0;
  const children = Array.from(container.children);
  const totalGap = gapValue * Math.max(0, children.length - 1);
  const reservedHeight = children.reduce((sum, child) => {
    if (child === boardEl) {
      return sum;
    }
    return sum + child.offsetHeight;
  }, 0);

  const availableHeight = container.clientHeight - reservedHeight - totalGap;
  const availableWidth = container.clientWidth;
  const heightLimit = Math.max(availableHeight, 0);
  const boardBound = Math.max(0, Math.min(availableWidth, heightLimit));

  boardEl.style.setProperty('--leader-board-bound', `${boardBound}px`);
  const rect = boardEl.getBoundingClientRect();
  const boardSizePx = Math.min(rect.width, rect.height);
  return { boardSizePx };
}

function handleReplayResize() {
  const boardEl = $('gameBoard');
  if (!boardEl) return;
  if (lastReplaySnapshot) {
    renderGameBoardAtIndex(lastReplaySnapshot);
  } else {
    ensureReplayBoardSizing(boardEl);
  }
}

function handleReplayKeyboard(event) {
  const target = event.target;
  if (target) {
    const tag = target.tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || target.isContentEditable) {
      return;
    }
  }

  if (!replayState.gameState || replayState.moveHistory.length === 0) {
    return;
  }

  let handled = false;
  const key = event.key;

  if (key === 'ArrowLeft') {
    setReplayIndex(replayState.index - 1);
    handled = true;
  } else if (key === 'ArrowRight') {
    setReplayIndex(replayState.index + 1);
    handled = true;
  } else if (key === ',' || key === '<') {
    setReplayIndex(0);
    handled = true;
  } else if (key === '.' || key === '>') {
    setReplayIndex(replayState.moveHistory.length);
    handled = true;
  }

  if (handled) {
    event.preventDefault();
  }
}
