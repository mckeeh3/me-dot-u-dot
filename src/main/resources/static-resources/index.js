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

function setStartGameButton(msg) {
  const el = $('start-game-btn');
  el.textContent = msg;
}

function setStatus(msg) {
  const el = $('status');
  el.textContent = msg;
}

function renderGameInfo() {
  if (!state.game) return;
  const p1 = state.game.player1Status;
  const p2 = state.game.player2Status;

  // Update status with turn and game state info
  const turnName = state.game.currentPlayer?.player?.name || '';
  const p1Type = p1.player.type === 'agent' ? '🤖' : '👤';
  const p2Type = p2.player.type === 'agent' ? '🤖' : '👤';

  if (state.game.status === 'in_progress') {
    const currentType = state.game.currentPlayer?.player?.type === 'agent' ? '🤖' : '👤';
    setStartGameButton('Reset Game');
    setStatus(`${currentType} ${turnName}'s turn • ${p1Type}${p1.player.name}: Score ${p1.score} • ${p2Type}${p2.player.name}: Score ${p2.score}`);
  } else if (state.game.status === 'won_by_player') {
    const winner = p1.isWinner ? p1 : p2;
    const winnerType = winner.player.type === 'agent' ? '🤖' : '👤';
    setStartGameButton('Start New Game');
    setStatus(`🎉 ${winnerType} ${winner.player.name} wins! • Final: ${p1Type}${p1.player.name}: ${p1.score} • ${p2Type}${p2.player.name}: ${p2.score}`);
  } else if (state.game.status === 'draw') {
    setStartGameButton('Start New Game');
    setStatus(`🤝 It's a draw! • Final: ${p1Type}${p1.player.name}: ${p1.score} • ${p2Type}${p2.player.name}: ${p2.score}`);
  } else if (state.game.status === 'canceled') {
    setStartGameButton('Start New Game');
    setStatus(`❌ Game canceled • ${p1Type}${p1.player.name}: ${p1.score} • ${p2Type}${p2.player.name}: ${p2.score}`);
  }
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
  const typeBtn = $(`${which}-type-btn`);
  const type = typeBtn ? typeBtn.textContent.trim() : 'human';
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
  const player = await loadPlayer(which);
  applyPlayerSelection(which, player);
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
  return player;
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
  setStartGameButton('Reset Game');
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
  setStatus("🎮 Let's play a game!");
  renderBoard();
});

async function startNewGameWizard() {
  const panel = $('setupPanel');
  panel.style.display = 'block';
  setStartGameButton('Reset Game');
  setStatus('👤 Select or create Player 1');

  // Reset level display back to dropdown
  $('levelDisplay').style.display = 'none';
  $('levelSetup').style.display = 'none'; // Will be shown after both players selected

  await populatePlayerMenu('p1');
  populateTypeMenu('p1');
  populateTypeMenu('p2');
  populateLevelMenu();
  $('p1Setup').style.display = 'block';
}

async function populatePlayerMenu(which) {
  const menu = $(`${which}-dd-menu`);
  const btn = $(`${which}-dd-btn`);
  menu.innerHTML = '';
  const players = await fetchPlayers();
  if (!players.length) {
    const empty = document.createElement('div');
    empty.className = 'dd-item';
    empty.textContent = 'No players yet';
    menu.appendChild(empty);
    btn.textContent = '— Select a player —';
    return;
  }
  players.forEach((p) => {
    const item = document.createElement('div');
    item.className = 'dd-item';
    item.textContent = `${p.name} (${p.type})`;
    item.onclick = (e) => {
      e.stopPropagation();
      btn.textContent = `${p.name} (${p.type})`;
      btn.dataset.playerId = p.id;
      ddClose(`${which}-dd`);
    };
    menu.appendChild(item);
  });
}

async function selectExistingPlayer(which) {
  const btn = $(`${which}-dd-btn`);
  const id = btn.dataset.playerId || '';
  if (!id) return;
  const res = await fetch(`/player/get-player/${encodeURIComponent(id)}`);
  const player = await res.json();
  applyPlayerSelection(which, player);
}

function applyPlayerSelection(which, player) {
  if (which === 'p1') {
    state.p1 = player;
    $('p1Setup').style.display = 'none';
    const summary = $('p1Summary');
    summary.style.display = 'block';
    summary.textContent = `Player 1: ${player.name} (${player.type})`;
    setStatus('👥 Select or create Player 2');
    // proceed to player 2
    $('p2Setup').style.display = 'block';
    populatePlayerMenu('p2');
  } else {
    // Prevent selecting the same player for Player 2
    if (state.p1 && player.id === state.p1.id) {
      state.p2 = null;
      // keep Player 2 selection visible and show an error
      $('p2Setup').style.display = 'block';
      const p2Summary = $('p2Summary');
      if (p2Summary) p2Summary.style.display = 'none';
      alert('Player 2 must be different from Player 1. Please choose another player.');
      return;
    }
    state.p2 = player;
    $('p2Setup').style.display = 'none';
    const summary = $('p2Summary');
    summary.style.display = 'block';
    summary.textContent = `Player 2: ${player.name} (${player.type})`;
    setStatus('🎯 Pick your game level');
    // enable level select and show Begin control as a next step in wizard
    $('levelSetup').style.display = 'block';
    $('beginControls').style.display = 'block';
    updateBeginButtonState();
  }
}

function ddToggle(id) {
  const el = $(id);
  el.classList.toggle('open');
}
function ddClose(id) {
  const el = $(id);
  el.classList.remove('open');
}

function populateTypeMenu(which) {
  const menu = $(`${which}-type-menu`);
  const btn = $(`${which}-type-btn`);
  menu.innerHTML = '';
  ['human', 'agent'].forEach((t) => {
    const item = document.createElement('div');
    item.className = 'dd-item';
    item.textContent = t;
    item.onclick = (e) => {
      e.stopPropagation();
      btn.textContent = t;
      ddClose(`${which}-type-dd`);
    };
    menu.appendChild(item);
  });
}

function populateLevelMenu() {
  const menu = $('level-menu');
  const btn = $('level-btn');
  if (!menu) return;
  menu.innerHTML = '';
  const levels = ['one', 'two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine'];
  levels.forEach((lvl) => {
    const item = document.createElement('div');
    item.className = 'dd-item';
    item.textContent = lvl;
    item.onclick = (e) => {
      e.stopPropagation();
      btn.textContent = lvl;
      ddClose('level-dd');
    };
    menu.appendChild(item);
  });
}

function updateBeginButtonState() {
  const begin = $('beginBtn');
  begin.disabled = !(state.p1 && state.p2);
}

async function beginGame() {
  if (!(state.p1 && state.p2)) return;
  const gameId = 'game-' + Date.now();
  const level = $('level-btn').textContent;
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
  setStatus(`🎮 Game started! ${state.game.currentPlayer?.player?.name || 'Player 1'}'s turn`);

  // Switch from level dropdown to read-only level display
  $('levelSetup').style.display = 'none';
  $('levelDisplay').style.display = 'block';
  $('level-value').textContent = level;

  // Hide the Begin Game button once game starts
  $('beginControls').style.display = 'none';

  renderGameInfo();
  renderBoard();
  openMoveStream(state.game.gameId);

  // If player 1 is an agent, trigger move
  const p1IsAgent = state.game.currentPlayer?.player?.id === state.p1.id && state.p1.type === 'agent';
  if (p1IsAgent) {
    // Let the backend and agent pipeline produce the move; frontend will refresh via SSE
    // TODO: add fetch to endpoint to trigger agent's first move
  }
}
