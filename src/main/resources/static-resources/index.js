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

  const turnName = state.game.currentPlayer?.player?.name || '';
  $('turn').textContent = state.game.status === 'in_progress' ? `Turn: ${turnName}` : `Status: ${state.game.status}`;
}

function renderBoard() {
  const board = $('gameBoard');
  board.innerHTML = '';
  const size = currentSize();
  board.style.setProperty('--size', size);

  const dots = state.game ? state.game.board.dots : [];
  const byId = new Map(dots.map((d) => [d.id, d]));

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

      cell.addEventListener('click', () => onCellClick(id));
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
  const url = `/game/get-move-stream-by-game-id?gameId=${encodeURIComponent(gameId)}`;
  evtSrc = new EventSource(url);
  evtSrc.onmessage = (e) => {
    try {
      const row = JSON.parse(e.data);
      // Optionally, we could refresh from server; for now, we only update status/labels
      if (row) {
        $('p1-label').textContent = row.player1Name;
        $('p2-label').textContent = row.player2Name;
        $('p1-score').textContent = row.player1Score;
        $('p2-score').textContent = row.player2Score;
        $('turn').textContent = row.status === 'in_progress' ? `Turn: ${row.currentPlayerName}` : `Status: ${row.status}`;
      }
    } catch {}
  };
}

document.addEventListener('DOMContentLoaded', () => {
  // Prefill ids
  $('p1-id').value = 'player-1';
  $('p2-id').value = 'player-2';
  $('game-id').value = 'game-' + Date.now();
  setStatus('Create players and a game to begin');
  renderBoard();
});
