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
  // This function is no longer used with the new UI
}

function setStatus(msg) {
  // This function is no longer used with the new UI
}

function setControlMessage(msg) {
  $('controlMessage').textContent = msg;
}

function startNewGameWizard() {
  // Reset state
  state.p1 = null;
  state.p2 = null;
  state.game = null;

  // Show player setup forms
  $('p1Setup').style.display = 'block';
  $('p1Stats').style.display = 'none';
  $('p2Setup').style.display = 'none';
  $('p2Stats').style.display = 'none';

  // Hide level selection and show control message
  $('levelSelection').style.display = 'none';
  $('controlMessage').style.display = 'block';

  // Hide control buttons
  $('resetBtn').style.display = 'none';
  $('cancelBtn').style.display = 'none';

  // Set initial message
  setControlMessage('Select your players');

  // Populate player dropdowns
  populatePlayerMenu('p1');
  populatePlayerMenu('p2');
  populateTypeMenu('p1');
  populateTypeMenu('p2');

  // Hide model dropdowns initially (they show when agent is selected)
  $('p1-model-dd').style.display = 'none';
  $('p2-model-dd').style.display = 'none';

  // Reset model button text
  $('p1-model-btn').textContent = 'Select Model';
  $('p2-model-btn').textContent = 'Select Model';

  // Set initial create button states (enabled for human, disabled for agent until model selected)
  updateCreateButtonState('p1');
  updateCreateButtonState('p2');

  // Reset create button text to default
  updateCreateButtonText('p1', false);
  updateCreateButtonText('p2', false);

  // Reset timers
  resetTimers();

  // Add real-time player ID validation
  setupPlayerIdValidation();
}

function resetGame() {
  if (state.game) {
    // Reset to setup mode
    startNewGameWizard();
  }
}

async function cancelGame() {
  if (!state.game || state.game.status !== 'in_progress') return;

  try {
    const res = await fetch('/game/cancel-game', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ gameId: state.game.gameId }),
    });

    if (res.ok) {
      const { gameState } = await res.json();
      state.game = gameState;
      renderGameInfo();
    }
  } catch (error) {
    console.error('Error canceling game:', error);
  }
}

// Initialize the UI on page load
function initializeUI() {
  setControlMessage('🎮 Me-Dot-U-Dot');
  populateTypeMenu('p1');
  populateTypeMenu('p2');
  populateLevelMenu();

  // Start with the wizard
  startNewGameWizard();

  // Ensure validation is set up after DOM is ready
  setTimeout(() => {
    setupPlayerIdValidation();
  }, 100);
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

    // Show reset and cancel buttons
    $('resetBtn').style.display = 'flex';
    $('cancelBtn').style.display = 'flex';

    // Update control center with turn info and game duration
    const gameDuration = formatTime(timerState.gameDurationSeconds);
    $('controlMessage').textContent = `${currentType} ${turnName}'s turn - Duration ${gameDuration}`;

    // Show player stats and hide setup forms
    $('p1Setup').style.display = 'none';
    $('p1Stats').style.display = 'flex';
    $('p2Setup').style.display = 'none';
    $('p2Stats').style.display = 'flex';

    // Update player stats
    updatePlayerStats('p1', p1);
    updatePlayerStats('p2', p2);

    // Hide level setup
    $('levelSelection').style.display = 'none';

    // Start game duration timer (only starts once)
    startGameDurationTimer();

    // Start timer for current player
    const currentPlayerId = state.game.currentPlayer?.player?.id;
    if (currentPlayerId === state.p1?.id) {
      startTimer('p1');
    } else if (currentPlayerId === state.p2?.id) {
      startTimer('p2');
    }
  } else if (state.game.status === 'won_by_player') {
    const winner = p1.isWinner ? p1 : p2;
    const winnerType = winner.player.type === 'agent' ? '🤖' : '👤';

    // Stop timers when game ends
    stopTimer();
    stopGameDurationTimer();

    // Show reset button, hide cancel button
    $('resetBtn').style.display = 'flex';
    $('cancelBtn').style.display = 'none';

    // Update final player stats
    updatePlayerStats('p1', p1);
    updatePlayerStats('p2', p2);

    const gameDuration = formatTime(timerState.gameDurationSeconds);
    $('controlMessage').textContent = `🎉 ${winnerType} ${winner.player.name} wins! - Duration ${gameDuration}`;
  } else if (state.game.status === 'draw') {
    // Stop timers when game ends
    stopTimer();
    stopGameDurationTimer();

    // Show reset button, hide cancel button
    $('resetBtn').style.display = 'flex';
    $('cancelBtn').style.display = 'none';

    // Update final player stats
    updatePlayerStats('p1', p1);
    updatePlayerStats('p2', p2);

    const gameDuration = formatTime(timerState.gameDurationSeconds);
    $('controlMessage').textContent = `🤝 It's a draw! - Duration ${gameDuration}`;
  } else if (state.game.status === 'canceled') {
    // Stop timers when game ends
    stopTimer();
    stopGameDurationTimer();

    // Show reset button, hide cancel button
    $('resetBtn').style.display = 'flex';
    $('cancelBtn').style.display = 'none';

    // Update final player stats
    updatePlayerStats('p1', p1);
    updatePlayerStats('p2', p2);

    const gameDuration = formatTime(timerState.gameDurationSeconds);
    $('controlMessage').textContent = `❌ Game canceled - Duration ${gameDuration}`;
  }
}

function updatePlayerStats(playerNum, playerStatus) {
  const avatar = playerStatus.player.type === 'agent' ? '🤖' : '👤';
  const type = playerStatus.player.type.toUpperCase();
  const model = playerStatus.player.model?.replace('ai-agent-model-', '') || '';
  $(`${playerNum}Avatar`).textContent = avatar;
  $(`${playerNum}Name`).textContent = playerStatus.player.name;
  $(`${playerNum}Type`).textContent = type === 'AGENT' ? model : 'HUMAN';
  $(`${playerNum}Score`).textContent = playerStatus.score;
  $(`${playerNum}Moves`).textContent = playerStatus.moves;

  // Show journal button only for agent players
  const journalBtn = $(`${playerNum}JournalBtn`);
  if (playerStatus.player.type === 'agent') {
    journalBtn.style.display = 'block';
  } else {
    journalBtn.style.display = 'none';
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

function renderPreviewBoard(level) {
  const board = $('gameBoard');
  board.innerHTML = '';
  const levelSizeMap = { one: 5, two: 7, three: 9, four: 11, five: 13, six: 15, seven: 17, eight: 19, nine: 21 };
  const size = levelSizeMap[level] || 5;
  board.style.setProperty('--size', size);
  const dotSizeBySize = { 5: 32, 7: 28, 9: 24, 11: 20, 13: 18, 15: 16, 17: 14, 19: 12, 21: 11 };
  const dotPx = dotSizeBySize[size] || 14;
  board.style.setProperty('--dot-size', `${dotPx}px`);

  for (let r = 0; r < size; r++) {
    for (let c = 0; c < size; c++) {
      const rowChar = String.fromCharCode('A'.charCodeAt(0) + r);
      const id = rowChar + (c + 1);
      const cell = document.createElement('div');
      cell.className = 'cell';
      cell.dataset.dotId = id;
      cell.style.pointerEvents = 'none'; // Preview board is not interactive
      board.appendChild(cell);
    }
  }
}

async function playMoveSound(oldState, newState) {
  // If no old state, don't play sounds (initial game creation)
  if (!oldState) return;

  // Check if game was won
  if (newState.status === 'won_by_player') {
    await playSound('game-win.wav');
    return;
  }

  // Check if game ended in draw
  if (newState.status === 'draw') {
    await playSound('game-lose.wav');
    return;
  }

  if (newState.status === 'in_progress') {
    // Check if either player scored
    const oldP1Score = oldState.player1Status?.score || 0;
    const oldP2Score = oldState.player2Status?.score || 0;
    const newP1Score = newState.player1Status?.score || 0;
    const newP2Score = newState.player2Status?.score || 0;

    if (newP1Score > oldP1Score || newP2Score > oldP2Score) {
      await playSound('game-score.wav');
      return;
    }

    // Check if either player's move count increased
    const oldP1Moves = oldState.player1Status?.moves || 0;
    const oldP2Moves = oldState.player2Status?.moves || 0;
    const newP1Moves = newState.player1Status?.moves || 0;
    const newP2Moves = newState.player2Status?.moves || 0;

    if (newP1Moves > oldP1Moves || newP2Moves > oldP2Moves) {
      await playSound('game-move.wav');
      return;
    }
  }
}

async function playSound(sound) {
  const audio = new Audio(`/sounds/${sound}`);
  audio.play();
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

  // Check for duplicate player ID
  const otherWhich = which === 'p1' ? 'p2' : 'p1';
  const otherPlayer = which === 'p1' ? state.p2 : state.p1;
  const otherIdField = $(`${otherWhich}-id`);

  if (otherPlayer && otherPlayer.id === id) {
    alert(`Player ID "${id}" is already used by ${otherWhich.toUpperCase()}. Please choose a different ID.`);
    return;
  }

  const otherCurrentId = otherIdField.value.trim();
  if (otherCurrentId && otherCurrentId === id) {
    alert(`Player ID "${id}" is already entered for ${otherWhich.toUpperCase()}. Please choose a different ID.`);
    return;
  }

  // Get model for agent players
  let model = '';
  if (type === 'agent') {
    const modelBtn = $(`${which}-model-btn`);
    model = modelBtn ? modelBtn.textContent.trim() : '';
    if (!model || model === 'Select Model') {
      alert('Model selection is required for agent players');
      return;
    }
  }

  const cmd = { id, type, name, model };
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

async function fetchAiModels() {
  try {
    const res = await fetch('/game/get-all-ai-agent-models', {
      headers: { Accept: 'application/json' },
    });
    if (!res.ok) {
      throw new Error(`Failed to fetch AI models: ${res.status} ${res.statusText}`);
    }
    const models = await res.json();
    return Array.isArray(models) ? models : [];
  } catch (error) {
    console.error('Error fetching AI models:', error);
    return [];
  }
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
  await playMoveSound(state.game, gameState);
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
      const event = JSON.parse(e.data); // we received an event; now fetch full state to render board
      if (event.lastAction === 'move_forfeited') {
        playSound('game-alarm.wav');
        alert(event.message);
      }
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
  await playMoveSound(state.game, gameState);
  state.game = gameState;
  renderGameInfo();
  renderBoard();
}

document.addEventListener('DOMContentLoaded', () => {
  // Prefill ids
  setStatus("🎮 Let's play a game!");
  renderBoard();
});

async function populatePlayerMenu(which) {
  const menu = $(`${which}-dd-menu`);
  const btn = $(`${which}-dd-btn`);
  menu.innerHTML = '';
  const allPlayers = await fetchPlayers();

  // Filter out the other player to prevent duplicates
  const otherPlayer = which === 'p1' ? state.p2 : state.p1;
  const players = allPlayers.filter((p) => !otherPlayer || p.id !== otherPlayer.id);

  if (!players.length) {
    const empty = document.createElement('div');
    empty.className = 'dd-item';
    empty.textContent = allPlayers.length > 0 ? 'No available players' : 'No players yet';
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

      // Populate create form fields with selected player data
      populateCreateForm(which, p);
    };
    menu.appendChild(item);
  });
}

async function populateCreateForm(which, player) {
  // Populate form fields with selected player data
  $(`${which}-id`).value = player.id;
  $(`${which}-name`).value = player.name;

  // Set player type dropdown
  const typeBtn = $(`${which}-type-btn`);
  typeBtn.textContent = player.type;

  // Show/hide model dropdown based on type
  const modelDropdown = $(`${which}-model-dd`);
  if (player.type === 'agent') {
    modelDropdown.style.display = 'block';

    // Always populate the model menu first to ensure options are available
    await populateModelMenu(which);

    // Set model if available, otherwise keep the default from populateModelMenu
    if (player.model) {
      const modelBtn = $(`${which}-model-btn`);
      modelBtn.textContent = player.model;
    }
  } else {
    modelDropdown.style.display = 'none';
    $(`${which}-model-btn`).textContent = 'Select Model';
  }

  // Update create button state and text
  updateCreateButtonState(which);
  updateCreateButtonText(which, true); // Set to "UPDATE & SELECT" for existing player

  // Make type and model fields read-only for existing players
  setTimeout(() => {
    setPlayerFormReadOnly(which, true, player);
  }, 0);
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

    // Populate form with selected player data and make it read-only
    populatePlayerForm('p1', player);

    // Use setTimeout to ensure the form is fully rendered before applying read-only styles
    setTimeout(() => {
      setPlayerFormReadOnly('p1', true, player);
    }, 0);

    // Update control message and show Player 2 setup
    setControlMessage('Select or create Player 2');
    $('p2Setup').style.display = 'block';
    // Refresh P2 menu to exclude the selected P1 player
    populatePlayerMenu('p2');
  } else {
    // Prevent selecting the same player for Player 2
    if (state.p1 && player.id === state.p1.id) {
      state.p2 = null;
      alert('Player 2 must be different from Player 1. Please choose another player.');
      return;
    }

    state.p2 = player;

    // Populate form with selected player data and make it read-only
    populatePlayerForm('p2', player);

    // Use setTimeout to ensure the form is fully rendered before applying read-only styles
    setTimeout(() => {
      setPlayerFormReadOnly('p2', true, player);
    }, 0);

    // Refresh P1 menu to exclude the selected P2 player
    populatePlayerMenu('p1');

    // Show level selection in control bar
    $('controlMessage').style.display = 'none';
    $('levelSelection').style.display = 'flex';
    updateBeginButtonState();
    // Show default preview board (level one)
    renderPreviewBoard('one');
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
    item.onclick = async (e) => {
      e.stopPropagation();
      btn.textContent = t;
      ddClose(`${which}-type-dd`);

      // Show/hide model dropdown based on type selection
      const modelDropdown = $(`${which}-model-dd`);
      if (t === 'agent') {
        modelDropdown.style.display = 'block';
        await populateModelMenu(which);
      } else {
        modelDropdown.style.display = 'none';
        // Reset model selection for human players
        $(`${which}-model-btn`).textContent = 'Select Model';
      }

      // Update create button state and text
      updateCreateButtonState(which);
      updateCreateButtonText(which, false); // Reset to "CREATE & SELECT"
    };
    menu.appendChild(item);
  });
}

async function populateModelMenu(which) {
  const menu = $(`${which}-model-menu`);
  const btn = $(`${which}-model-btn`);
  menu.innerHTML = '';

  try {
    const models = await fetchAiModels();
    if (models.length === 0) {
      const item = document.createElement('div');
      item.className = 'dd-item';
      item.textContent = 'No models available';
      item.style.opacity = '0.5';
      menu.appendChild(item);
      return;
    }

    models.forEach((model, index) => {
      const item = document.createElement('div');
      item.className = 'dd-item';
      item.textContent = model;
      item.onclick = (e) => {
        e.stopPropagation();
        btn.textContent = model;
        ddClose(`${which}-model-dd`);
        updateCreateButtonState(which);
      };
      menu.appendChild(item);
    });

    // Set default selection to first model
    if (models.length > 0) {
      btn.textContent = models[0];
    }
  } catch (error) {
    console.error('Error populating model menu:', error);
    const item = document.createElement('div');
    item.className = 'dd-item';
    item.textContent = 'Error loading models';
    item.style.opacity = '0.5';
    menu.appendChild(item);
  }
}

function updateCreateButtonState(which) {
  const typeBtn = $(`${which}-type-btn`);
  const modelBtn = $(`${which}-model-btn`);
  const createBtn = $(`${which}-create-btn`);

  const isAgent = typeBtn.textContent === 'agent';
  const hasModel = isAgent ? modelBtn.textContent !== 'Select Model' : true;

  createBtn.disabled = !hasModel;
}

function updateCreateButtonText(which, isExistingPlayer) {
  const createBtn = $(`${which}-create-btn`);
  if (createBtn) {
    createBtn.textContent = isExistingPlayer ? 'UPDATE & SELECT' : 'CREATE & SELECT';
  }
}

// Define validation handlers as named functions to allow removal
const p1ValidationHandler = async () => {
  validatePlayerIdInput('p1');
  await checkExistingPlayer('p1');
};

const p2ValidationHandler = async () => {
  validatePlayerIdInput('p2');
  await checkExistingPlayer('p2');
};

// Define focus handlers to check existing player when field gets focus
const p1FocusHandler = async () => {
  await checkExistingPlayer('p1');
};

const p2FocusHandler = async () => {
  await checkExistingPlayer('p2');
};

function setupPlayerIdValidation() {
  // Add input and focus event listeners for real-time validation
  const p1IdField = $('p1-id');
  const p2IdField = $('p2-id');

  if (p1IdField) {
    // Remove any existing listeners first
    p1IdField.removeEventListener('input', p1ValidationHandler);
    p1IdField.removeEventListener('focus', p1FocusHandler);
    // Add input and focus listeners
    p1IdField.addEventListener('input', p1ValidationHandler);
    p1IdField.addEventListener('focus', p1FocusHandler);
  }

  if (p2IdField) {
    // Remove any existing listeners first
    p2IdField.removeEventListener('input', p2ValidationHandler);
    p2IdField.removeEventListener('focus', p2FocusHandler);
    // Add input and focus listeners
    p2IdField.addEventListener('input', p2ValidationHandler);
    p2IdField.addEventListener('focus', p2FocusHandler);
  }
}

function validatePlayerIdInput(which) {
  const currentId = $(`${which}-id`).value.trim();
  if (!currentId) return; // Don't validate empty fields

  const otherWhich = which === 'p1' ? 'p2' : 'p1';
  const otherPlayer = which === 'p1' ? state.p2 : state.p1;
  const otherIdField = $(`${otherWhich}-id`);

  // Check against the other player's selected player (if any)
  if (otherPlayer && otherPlayer.id === currentId) {
    alert(`Player ID "${currentId}" is already used by ${otherWhich.toUpperCase()}. Please choose a different ID.`);
    return;
  }

  // Check against the other player's form field (if filled)
  const otherCurrentId = otherIdField.value.trim();
  if (otherCurrentId && otherCurrentId === currentId) {
    alert(`Player ID "${currentId}" is already entered for ${otherWhich.toUpperCase()}. Please choose a different ID.`);
    return;
  }
}

async function checkExistingPlayer(which) {
  const playerId = $(`${which}-id`).value.trim();
  if (!playerId) {
    // Clear form and make fields editable when ID is empty
    clearPlayerForm(which);
    setPlayerFormReadOnly(which, false);
    return;
  }

  try {
    // Fetch players directly to check if player exists
    const allPlayers = await fetchPlayers();
    const existingPlayer = allPlayers.find((p) => p.id === playerId);

    if (existingPlayer) {
      // Populate form with existing player data
      populatePlayerForm(which, existingPlayer);
      // Make type and model read-only (with timeout to ensure DOM is ready)
      setTimeout(() => {
        setPlayerFormReadOnly(which, true, existingPlayer);
      }, 0);
      // Update button text for existing player
      updateCreateButtonText(which, true);
    } else {
      // Don't clear form fields, just make type/model editable for new players
      setPlayerFormReadOnly(which, false);
      // Update button text for new player
      updateCreateButtonText(which, false);
    }
  } catch (error) {
    console.error('Error checking existing player:', error);
    // On error, just make fields editable
    setPlayerFormReadOnly(which, false);
  }
}

function populatePlayerForm(which, player) {
  const nameField = $(`${which}-name`);
  if (nameField) {
    nameField.value = player.name || '';
  }

  // Update type dropdown button text
  const typeBtn = $(`${which}-type-btn`);
  if (typeBtn) {
    // Use the same values as the dropdown menu for consistency
    typeBtn.textContent = player.type; // 'agent' or 'human'
  }

  // Update model dropdown if it's an agent
  const modelGroup = $(`${which}-model-group`);
  if (player.type === 'agent') {
    const modelBtn = $(`${which}-model-btn`);
    if (modelBtn) {
      modelBtn.textContent = player.model || 'Select Model';
    }
    if (modelGroup) {
      modelGroup.style.display = 'block';
    }
  } else {
    if (modelGroup) {
      modelGroup.style.display = 'none';
    }
  }

  updateCreateButtonState(which);
}

function clearPlayerForm(which, clearId = true) {
  if (clearId) {
    const idField = $(`${which}-id`);
    if (idField) {
      idField.value = '';
    }
  }

  const nameField = $(`${which}-name`);
  if (nameField) {
    nameField.value = '';
  }

  // Reset type dropdown
  const typeBtn = $(`${which}-type-btn`);
  if (typeBtn) {
    typeBtn.textContent = 'Select Type';
  }

  // Reset model dropdown
  const modelBtn = $(`${which}-model-btn`);
  if (modelBtn) {
    modelBtn.textContent = 'Select Model';
  }

  const modelGroup = $(`${which}-model-group`);
  if (modelGroup) {
    modelGroup.style.display = 'none';
  }

  updateCreateButtonState(which);
  updateCreateButtonText(which, false); // Reset to "CREATE & SELECT"
}

function setPlayerFormReadOnly(which, readOnly, player = null) {
  const typeBtn = $(`${which}-type-btn`);
  const modelBtn = $(`${which}-model-btn`);

  if (readOnly) {
    // Always make type dropdown non-interactive for existing players
    if (typeBtn) {
      typeBtn.style.pointerEvents = 'none';
      typeBtn.style.opacity = '0.6';
      typeBtn.style.cursor = 'not-allowed';
    }

    // Only make model dropdown non-interactive if player is an agent
    if (modelBtn && player && player.type === 'agent') {
      modelBtn.style.pointerEvents = 'none';
      modelBtn.style.opacity = '0.6';
      modelBtn.style.cursor = 'not-allowed';
    }
  } else {
    // Make type and model dropdowns interactive
    if (typeBtn) {
      typeBtn.style.pointerEvents = 'auto';
      typeBtn.style.opacity = '1';
      typeBtn.style.cursor = 'pointer';
    }
    if (modelBtn) {
      modelBtn.style.pointerEvents = 'auto';
      modelBtn.style.opacity = '1';
      modelBtn.style.cursor = 'pointer';
    }
  }
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
      // Show preview board for selected level
      renderPreviewBoard(lvl);
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
  const gameId = 'game-' + new Date().toISOString();
  const level = $('level-btn').textContent;
  const req = {
    gameId,
    player1: { id: state.p1.id, type: state.p1.type, name: state.p1.name, model: state.p1.model },
    player2: { id: state.p2.id, type: state.p2.type, name: state.p2.name, model: state.p2.model },
    level,
  };
  const res = await fetch('/game/create-game', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  });
  const { gameState } = await res.json();
  state.game = gameState;
  // Hide level selection and show control message
  $('levelSelection').style.display = 'none';
  $('controlMessage').style.display = 'block';

  renderGameInfo();
  renderBoard();
  openMoveStream(state.game.gameId);
}

// Journal viewer state
const journalState = {
  currentAgentId: null,
  currentSequenceId: null,
  isViewing: false,
};

async function openJournalViewer(playerNum) {
  const player = playerNum === 'p1' ? state.p1 : state.p2;
  if (!player || player.type !== 'agent') return;

  // Toggle: if journal is already viewing this agent, close it
  if (journalState.isViewing && journalState.currentAgentId === player.id) {
    exitJournalViewer();
    return;
  }

  journalState.currentAgentId = player.id;
  journalState.currentSequenceId = Number.MAX_SAFE_INTEGER; // Start with max value
  journalState.isViewing = true;

  // Hide game board and show journal viewer
  $('gameBoard').style.display = 'none';
  $('journalViewer').style.display = 'flex';

  // Load initial journal entry
  await loadJournalEntry();
}

function exitJournalViewer() {
  journalState.isViewing = false;
  journalState.currentAgentId = null;
  journalState.currentSequenceId = null;

  // Show game board and hide journal viewer
  $('gameBoard').style.display = 'grid';
  $('journalViewer').style.display = 'none';
}

async function navigateJournal(direction) {
  if (!journalState.currentAgentId) return;

  const endpoint = direction === 'up' ? '/game/get-journal-by-agent-id-up' : '/game/get-journal-by-agent-id-down';

  try {
    const res = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        agentId: journalState.currentAgentId,
        sequenceId: journalState.currentSequenceId,
      }),
    });

    if (res.ok) {
      const data = await res.json();
      if (data.journals && data.journals.length > 0) {
        const entry = data.journals[0];
        journalState.currentSequenceId = entry.sequenceId;
        displayJournalEntry(entry);
      } else {
        // No more entries in this direction
        const instructions = direction === 'up' ? 'No more recent journal entries.' : 'No more previous journal entries.';
        $('journalInstructions').textContent = instructions;
      }
    }
  } catch (error) {
    console.error('Error navigating journal:', error);
    $('journalInstructions').textContent = 'Error loading journal entry.';
  }
}

async function loadJournalEntry() {
  if (!journalState.currentAgentId) return;

  try {
    const res = await fetch('/game/get-journal-by-agent-id-down', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        agentId: journalState.currentAgentId,
        sequenceId: journalState.currentSequenceId,
      }),
    });

    if (res.ok) {
      const data = await res.json();
      if (data.journals && data.journals.length > 0) {
        const entry = data.journals[0];
        journalState.currentSequenceId = entry.sequenceId;
        displayJournalEntry(entry);
      } else {
        // No journal entries found
        $('journalAgentId').textContent = journalState.currentAgentId;
        $('journalSequenceId').textContent = '-';
        $('journalInstructions').textContent = 'No journal entries found for this agent.';
      }
    }
  } catch (error) {
    console.error('Error loading journal entry:', error);
    $('journalAgentId').textContent = journalState.currentAgentId;
    $('journalSequenceId').textContent = '-';
    $('journalInstructions').textContent = 'Error loading journal entries.';
  }
}

function displayJournalEntry(entry) {
  const updatedAt = entry.updatedAt ? new Date(entry.updatedAt).toLocaleString() : '-';
  $('journalAgentId').textContent = entry.agentId;
  $('journalSequenceId').textContent = entry.sequenceId;
  $('journalUpdatedAt').textContent = updatedAt;
  $('journalInstructions').textContent = entry.instructions || 'No instructions available.';
}

// Timer functionality
const timerState = {
  p1Seconds: 0,
  p2Seconds: 0,
  activePlayer: null,
  intervalId: null,
  // Game duration timer
  gameDurationSeconds: 0,
  gameDurationIntervalId: null,
  gameDurationStarted: false,
};

function formatTime(seconds) {
  if (seconds < 60) {
    return `${seconds}s`;
  }
  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = seconds % 60;
  return `${minutes}m${remainingSeconds}s`;
}

function updateTimerDisplay() {
  $('p1Timer').textContent = formatTime(timerState.p1Seconds);
  $('p2Timer').textContent = formatTime(timerState.p2Seconds);
}

function startTimer(playerNum) {
  // Stop any existing timer
  stopTimer();

  // Reset the active player's timer
  if (playerNum === 'p1') {
    timerState.p1Seconds = 0;
    timerState.activePlayer = 'p1';
  } else {
    timerState.p2Seconds = 0;
    timerState.activePlayer = 'p2';
  }

  // Update display immediately
  updateTimerDisplay();

  // Start the interval timer
  timerState.intervalId = setInterval(() => {
    if (timerState.activePlayer === 'p1') {
      timerState.p1Seconds++;
    } else if (timerState.activePlayer === 'p2') {
      timerState.p2Seconds++;
    }
    updateTimerDisplay();
  }, 1000);
}

function stopTimer() {
  if (timerState.intervalId) {
    clearInterval(timerState.intervalId);
    timerState.intervalId = null;
  }
  timerState.activePlayer = null;
}

function resetTimers() {
  stopTimer();
  resetGameDurationTimer();
  timerState.p1Seconds = 0;
  timerState.p2Seconds = 0;
  updateTimerDisplay();
}

// Game duration timer functions
function startGameDurationTimer() {
  if (timerState.gameDurationStarted) return; // Already started

  timerState.gameDurationStarted = true;
  timerState.gameDurationSeconds = 0;

  timerState.gameDurationIntervalId = setInterval(() => {
    timerState.gameDurationSeconds++;
    updateGameDurationDisplay();
  }, 1000);
}

function stopGameDurationTimer() {
  if (timerState.gameDurationIntervalId) {
    clearInterval(timerState.gameDurationIntervalId);
    timerState.gameDurationIntervalId = null;
  }
}

function resetGameDurationTimer() {
  stopGameDurationTimer();
  timerState.gameDurationSeconds = 0;
  timerState.gameDurationStarted = false;
}

function updateGameDurationDisplay() {
  // Only update if we're in an active game
  if (!state.game || state.game.status !== 'in_progress') return;

  const currentPlayer = state.game.currentPlayer?.player;
  if (!currentPlayer) return;

  const currentType = currentPlayer.type === 'agent' ? '🤖' : '👤';
  const gameDuration = formatTime(timerState.gameDurationSeconds);
  $('controlMessage').textContent = `${currentType} ${currentPlayer.name}'s turn - Duration ${gameDuration}`;
}

// Navigation menu functions
function toggleMenu() {
  const popup = $('menuPopup');
  if (popup) {
    const isVisible = popup.style.display !== 'none';
    popup.style.display = isVisible ? 'none' : 'block';

    // Add click outside listener if menu is shown
    if (!isVisible) {
      setTimeout(() => {
        document.addEventListener('click', closeMenuOnClickOutside);
      }, 0);
    }
  }
}

function closeMenuOnClickOutside(e) {
  const popup = $('menuPopup');
  const menuBtn = document.querySelector('.menu-btn');

  if (popup && !popup.contains(e.target) && !menuBtn.contains(e.target)) {
    popup.style.display = 'none';
    document.removeEventListener('click', closeMenuOnClickOutside);
  }
}

// Initialize the UI when the page loads
window.addEventListener('DOMContentLoaded', initializeUI);
