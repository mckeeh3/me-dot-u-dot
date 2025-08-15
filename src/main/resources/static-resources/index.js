let state = {
  game: null,
  p1: null,
  p2: null,
};

function $(id) {
  return document.getElementById(id);
}

function currentSize() {
  if (!state.game) return 5;
  const level = state.game.board.level; // enum name string
  const map = { one: 5, two: 7, three: 9, four: 11, five: 13, six: 15, seven: 17, eight: 19, nine: 21 };
  return map[level] || 5;
}

function setStatus(msg) {
  const el = $('status');
  el.textContent = msg;
}

function renderGameInfo() {
  if (!state.game) return;
  const p1 = state.game.player1Status;
  const p2 = state.game.player2Status;
  $('p1-label').textContent = `${p1.player.name}`;
  $('p2-label').textContent = `${p2.player.name}`;
  $('p1-score').textContent = p1.score;
  $('p2-score').textContent = p2.score;
  const p1MovesEl = $('p1-moves');
  const p2MovesEl = $('p2-moves');
  if (p1MovesEl) p1MovesEl.textContent = p1.moves;
  if (p2MovesEl) p2MovesEl.textContent = p2.moves;

  const turnName = state.game.currentPlayer?.player?.name || '';
  $('turn').textContent = state.game.status === 'in_progress' ? `Turn: ${turnName}` : `Status: ${state.game.status}`;
}

function renderBoard() {
  const board = $('gameBoard');
  board.innerHTML = '';
  const size = currentSize();
  board.style.setProperty('--size', size);
  const dotSizeBySize = { 5: 32, 7: 28, 9: 24, 11: 20, 13: 18, 15: 16, 17: 14, 19: 12, 21: 11 };
  const dotPx = dotSizeBySize[size] || 14;
  board.style.setProperty('--dot-size', `${dotPx}px`);

  const dots = state.game ? state.game.board.dots : [];
  const byId = new Map(dots.map((d) => [d.id, d]));
  const lastMoveId = state.game?.moveHistory?.length ? state.game.moveHistory[state.game.moveHistory.length - 1].dotId : null;

  for (let r = 0; r < size; r++) {
    for (let c = 0; c < size; c++) {
      const rowChar = String.fromCharCode('A'.charCodeAt(0) + r);
      const id = rowChar + (c + 1);
      const d = byId.get(id);
      const cell = document.createElement('div');
      cell.className = 'cell';
      cell.dataset.dotId = id;

      if (d && d.player && d.player.id) {
        const pid = d.player.id;
        const cls = pid === state.p1?.id ? 'player' : pid === state.p2?.id ? 'ai' : '';
        if (cls) cell.classList.add(cls);
        cell.textContent = '●';
      }
      if (lastMoveId && id === lastMoveId) {
        cell.classList.add('last-move');
      }

      const isAgentsTurn = state.game && state.game.currentPlayer && state.game.currentPlayer.player && state.game.currentPlayer.player.type === 'agent';
      const isInProgress = state.game && state.game.status === 'in_progress';
      const isOccupied = !!(d && d.player && d.player.id);
      if (!isAgentsTurn && isInProgress && !isOccupied) {
        cell.addEventListener('click', () => onCellClick(id));
      } else {
        cell.style.pointerEvents = 'none';
      }
      board.appendChild(cell);
    }
  }
}

async function createPlayer(which) {
  const id = $(which + '-id').value.trim();
  const name = $(which + '-name').value.trim();
  const type = $(which + '-type').value;
  if (!id || !name) {
    alert('Player id and name are required');
    return;
  }

  const cmd = { id, type, name };
  await fetch('/player/create-player', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(cmd),
  });
  await loadPlayer(which);
}

async function loadPlayer(which) {
  const id = $(which + '-id').value.trim();
  if (!id) {
    alert('Enter player id');
    return;
  }
  const res = await fetch(`/player/get-player/${encodeURIComponent(id)}`);
  const player = await res.json();
  if (which === 'p1') state.p1 = player;
  else state.p2 = player;
  renderGameInfo();
}

// Fetch all players (id, name, type) from the backend
async function fetchPlayers() {
  const res = await fetch('/player/get-players', {
    headers: { Accept: 'application/json' },
  });
  if (!res.ok) {
    throw new Error(`Failed to fetch players: ${res.status} ${res.statusText}`);
  }
  const { players } = await res.json();
  return Array.isArray(players) ? players : [];
}

async function createGame() {
  const gameId = $('game-id').value.trim() || 'game-' + Date.now();
  const level = $('level').value;
  if (!state.p1 || !state.p2) {
    alert('Load or create both players first');
    return;
  }

  const req = {
    gameId,
    player1: { id: state.p1.id, type: state.p1.type, name: state.p1.name },
    player2: { id: state.p2.id, type: state.p2.type, name: state.p2.name },
    level,
  };
  const res = await fetch('/game/create-game', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  });
  const { gameState } = await res.json();
  state.game = gameState;
  $('game-id').value = state.game.gameId;
  setStatus('Game created');
  renderGameInfo();
  renderBoard();

  openMoveStream(state.game.gameId);
}

async function onCellClick(dotId) {
  if (!state.game || state.game.status !== 'in_progress') return;
  const current = state.game.currentPlayer?.player?.id;
  if (!current) return;

  const req = { gameId: state.game.gameId, playerId: current, dotId };
  const res = await fetch('/game/make-move', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  });
  const { gameState } = await res.json();
  state.game = gameState;
  renderGameInfo();
  renderBoard();
}

let evtSrc;
function openMoveStream(gameId) {
  if (evtSrc) evtSrc.close();
  const url = `/game/get-move-stream-by-game-id/${encodeURIComponent(gameId)}`;
  evtSrc = new EventSource(url);
  evtSrc.onmessage = (e) => {
    try {
      const _ = JSON.parse(e.data); // we received an event; now fetch full state to render board
      refreshGameState();
    } catch {}
  };
}

async function refreshGameState() {
  if (!state.game?.gameId) return;
  const res = await fetch(`/game/get-state/${encodeURIComponent(state.game.gameId)}`, {
    headers: { Accept: 'application/json' },
  });
  const { gameState } = await res.json();
  state.game = gameState;
  renderGameInfo();
  renderBoard();
}

document.addEventListener('DOMContentLoaded', () => {
  // Prefill ids
  $('p1-id').value = 'player-1';
  $('p2-id').value = 'player-2';
  $('game-id').value = 'game-' + Date.now();
  setStatus('Create players and a game to begin');
  renderBoard();
});
