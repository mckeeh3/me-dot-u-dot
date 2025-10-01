const LOG_PAGE_SIZE = 25;

let recentGames = [];
let selectedGameId = null;
let selectedLogId = null;

const logPageState = {
  pageIndex: 0,
  offset: 0,
  hasMore: false,
};

document.addEventListener('DOMContentLoaded', () => {
  initEventListeners();
  loadRecentGames();
});

function initEventListeners() {
  const refreshBtn = $('gamesRefreshBtn');
  if (refreshBtn) {
    refreshBtn.addEventListener('click', loadRecentGames);
  }

  const prevBtn = $('logsPrevPageBtn');
  const nextBtn = $('logsNextPageBtn');

  if (prevBtn) {
    prevBtn.addEventListener('click', goToPreviousLogPage);
  }

  if (nextBtn) {
    nextBtn.addEventListener('click', goToNextLogPage);
  }
}

async function loadRecentGames() {
  const tbody = $('recentGamesBody');
  if (!tbody) {
    return;
  }

  tbody.innerHTML = `
      <tr class="game-list-empty-row">
        <td>Loading recent games…</td>
      </tr>
    `;

  try {
    const limit = 50;
    const response = await fetch('/game/get-recent-games', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify({ limit: limit, offset: 0 }),
    });
    if (!response.ok) {
      throw new Error(`Failed to load recent games (${response.status})`);
    }

    const data = await response.json();
    recentGames = Array.isArray(data.games) ? data.games : [];

    renderRecentGames();

    if (recentGames.length > 0) {
      selectGame(recentGames[0].gameId);
    } else {
      clearLogPanels();
    }
  } catch (error) {
    console.error('Error loading recent games:', error);
    tbody.innerHTML = `
        <tr class="game-list-empty-row">
          <td>Unable to load recent games.</td>
        </tr>
      `;
  }
}

function renderRecentGames() {
  const tbody = $('recentGamesBody');
  if (!tbody) {
    return;
  }

  tbody.innerHTML = '';

  if (recentGames.length === 0) {
    tbody.innerHTML = `
        <tr class="game-list-empty-row">
          <td>No recent games found.</td>
        </tr>
      `;
    return;
  }

  recentGames.forEach((game) => {
    const row = document.createElement('tr');
    row.dataset.gameId = game.gameId;
    const created = formatDateTime(game.createdAt);

    row.innerHTML = `
        <td data-label="Started">${created}</td>
      `;

    row.addEventListener('click', () => selectGame(game.gameId));

    if (game.gameId === selectedGameId) {
      row.classList.add('selected');
    }

    tbody.appendChild(row);
  });
}

function selectGame(gameId) {
  if (!gameId) {
    return;
  }

  selectedGameId = gameId;
  selectedLogId = null;
  resetLogPagination();
  updateRecentGameSelection();
  loadLogsForSelectedGame();
}

function updateRecentGameSelection() {
  document.querySelectorAll('.game-list-table tbody tr').forEach((row) => {
    row.classList.toggle('selected', row.dataset.gameId === selectedGameId);
  });
}

function resetLogPagination() {
  logPageState.pageIndex = 0;
  logPageState.offset = 0;
  logPageState.hasMore = false;
  updatePaginationControls();
}

async function loadLogsForSelectedGame() {
  const tbody = $('gameLogsBody');
  if (!tbody || !selectedGameId) {
    return;
  }

  tbody.innerHTML = `
      <tr class="log-list-empty-row">
        <td colspan="4">Loading log messages…</td>
      </tr>
    `;

  try {
    const response = await fetch('/game-action-log/get-logs-by-game', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify({
        gameId: selectedGameId,
        limit: LOG_PAGE_SIZE,
        offset: logPageState.offset,
      }),
    });
    if (!response.ok) {
      throw new Error(`Failed to load logs (${response.status})`);
    }

    const data = await response.json();
    const logs = Array.isArray(data.logs) ? data.logs : [];

    logPageState.hasMore = Boolean(data.hasMore);

    renderLogRows(logs);
    updatePaginationControls();

    if (logs.length > 0) {
      selectLog(logs[0].id, true);
    } else {
      selectedLogId = null;
      renderLogDetailPlaceholder('No log entries found for this game.');
    }
  } catch (error) {
    console.error('Error loading game logs:', error);
    tbody.innerHTML = `
        <tr class="log-list-empty-row">
          <td colspan="4">Unable to load logs.</td>
        </tr>
      `;
    renderLogDetailPlaceholder('Select a game to view log details.');
  }
}

function renderLogRows(logs) {
  const tbody = $('gameLogsBody');
  if (!tbody) {
    return;
  }

  tbody.innerHTML = '';

  if (logs.length === 0) {
    tbody.innerHTML = `
        <tr class="log-list-empty-row">
          <td colspan="4">No log entries found.</td>
        </tr>
      `;
    return;
  }

  logs.forEach((log) => {
    const row = document.createElement('tr');
    row.dataset.logId = log.id;
    row.dataset.logTime = log.time;

    const time = formatDateTime(log.time);
    const summary = summariseMessage(log.message);

    row.innerHTML = `
        <td data-label="Time">${time}</td>
        <td data-label="Player">${log.playerId || '—'}</td>
        <td data-label="Type">${formatLogType(log.type)}</td>
        <td data-label="Summary">${summary}</td>
      `;

    row.addEventListener('click', () => selectLog(log.id, false));

    if (log.id === selectedLogId) {
      row.classList.add('selected');
    }

    tbody.appendChild(row);
  });
}

function selectLog(logId, autoSelected = false) {
  if (!logId) {
    return;
  }

  selectedLogId = logId;
  document.querySelectorAll('.log-list-table tbody tr').forEach((row) => {
    row.classList.toggle('selected', row.dataset.logId === logId);
  });

  loadLogDetail(logId, autoSelected);
}

async function loadLogDetail(logId, autoSelected) {
  const detailContainer = $('logDetail');
  if (!detailContainer) {
    return;
  }

  if (!autoSelected) {
    detailContainer.innerHTML = '<div class="log-detail-placeholder">Loading log detail…</div>';
  }

  try {
    let response = await fetch(`/game-action-log/get-log-by-id/${encodeURIComponent(logId)}`);

    if (!response.ok && response.status === 404) {
      response = await fetch(`/game-action-log/get-log-by-id?logMessageId=${encodeURIComponent(logId)}`);
    }

    if (!response.ok) {
      throw new Error(`Failed to load log detail (${response.status})`);
    }

    const log = await response.json();
    renderLogDetail(log);
  } catch (error) {
    console.error('Error loading log detail:', error);
    renderLogDetailPlaceholder('Unable to load log detail.');
  }
}

function renderLogDetail(log) {
  const detailContainer = $('logDetail');
  if (!detailContainer) {
    return;
  }

  if (!log) {
    renderLogDetailPlaceholder('No log detail available.');
    return;
  }

  const time = formatDateTime(log.time);

  detailContainer.innerHTML = `
      <div class="log-meta">
        <div class="log-meta-item">
          <span class="log-meta-label">Log ID</span>
          <span class="log-meta-value">${log.id}</span>
        </div>
        <div class="log-meta-item">
          <span class="log-meta-label">Game</span>
          <span class="log-meta-value">${log.gameId}</span>
        </div>
        <div class="log-meta-item">
          <span class="log-meta-label">Player</span>
          <span class="log-meta-value">${log.playerId || '—'}</span>
        </div>
        <div class="log-meta-item">
          <span class="log-meta-label">Type</span>
          <span class="log-meta-value">${formatLogType(log.type)}</span>
        </div>
        <div class="log-meta-item">
          <span class="log-meta-label">Time</span>
          <span class="log-meta-value">${time}</span>
        </div>
      </div>
      <div class="log-message">${escapeHtml(log.message)}</div>
    `;
}

function renderLogDetailPlaceholder(message) {
  const detailContainer = $('logDetail');
  if (!detailContainer) {
    return;
  }

  detailContainer.innerHTML = `<div class="log-detail-placeholder">${message}</div>`;
}

function goToNextLogPage() {
  if (!logPageState.hasMore) {
    return;
  }

  logPageState.pageIndex += 1;
  logPageState.offset = logPageState.pageIndex * LOG_PAGE_SIZE;
  loadLogsForSelectedGame();
}

function goToPreviousLogPage() {
  if (logPageState.pageIndex === 0) {
    return;
  }

  logPageState.pageIndex -= 1;
  logPageState.offset = logPageState.pageIndex * LOG_PAGE_SIZE;
  loadLogsForSelectedGame();
}

function updatePaginationControls() {
  const prevBtn = $('logsPrevPageBtn');
  const nextBtn = $('logsNextPageBtn');
  const status = $('logsPageStatus');

  if (prevBtn) {
    prevBtn.disabled = logPageState.pageIndex === 0;
  }

  if (nextBtn) {
    nextBtn.disabled = !logPageState.hasMore;
  }

  if (status) {
    status.textContent = `Page ${logPageState.pageIndex + 1}`;
  }
}

function clearLogPanels() {
  renderLogRows([]);
  renderLogDetailPlaceholder('Select a game to view log details.');
}

function formatDateTime(value) {
  if (!value) {
    return '—';
  }

  try {
    const date = new Date(value);
    return date.toLocaleString(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  } catch (e) {
    return value;
  }
}

function formatLogType(type) {
  if (!type) {
    return 'Unknown';
  }
  const text = type.toString().replace(/_/g, ' ');
  return text.charAt(0).toUpperCase() + text.slice(1);
}

function summariseMessage(message) {
  if (!message) {
    return '—';
  }
  const trimmed = message.trim();
  if (trimmed.length <= 60) {
    return escapeHtml(trimmed);
  }
  return `${escapeHtml(trimmed.slice(0, 57))}…`;
}

function escapeHtml(text) {
  if (!text) {
    return '';
  }

  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}
