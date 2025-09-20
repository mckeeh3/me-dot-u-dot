// Journal state
const journalState = {
  isViewing: false,
  currentAgentId: null,
  currentSequenceId: Number.MAX_SAFE_INTEGER,
  currentAgentMeta: null,
  showDiff: false,
};

const agentListState = {
  agents: [],
};

async function loadAgentList() {
  const body = $('agentListBody');
  if (!body) return;

  body.innerHTML = `
    <tr class="journal-list-empty-row">
      <td colspan="3">Loading agent players…</td>
    </tr>
  `;

  try {
    const response = await fetchJson('/player/get-players');
    const players = response.players || [];

    const agentPlayers = players.filter((p) => p.type?.toLowerCase() === 'agent');
    agentPlayers.sort((a, b) => (a.name || '').localeCompare(b.name || ''));

    agentListState.agents = agentPlayers;
    renderAgentList();
  } catch (error) {
    console.error('Error fetching players:', error);
    body.innerHTML = `
      <tr class="journal-list-empty-row">
        <td colspan="3">Error loading agent players.</td>
      </tr>
    `;
  }
}

// Backward compatibility: older entry points may still call populatePlayerMenu.
async function populatePlayerMenu() {
  await loadAgentList();
}

function renderAgentList() {
  const body = $('agentListBody');
  if (!body) return;

  body.innerHTML = '';

  if (!agentListState.agents.length) {
    body.innerHTML = `
      <tr class="journal-list-empty-row">
        <td colspan="3">No agent players available yet.</td>
      </tr>
    `;
    return;
  }

  agentListState.agents.forEach((agent) => {
    const row = document.createElement('tr');
    row.className = 'journal-list-row';
    row.dataset.agentId = agent.id;

    const nameCell = document.createElement('td');
    nameCell.className = 'agent-name-cell';
    nameCell.textContent = agent.name || agent.id;

    const modelCell = document.createElement('td');
    modelCell.className = 'agent-model-cell';
    modelCell.textContent = agent.model || 'Model not set';

    const idCell = document.createElement('td');
    idCell.className = 'agent-id-cell';
    idCell.textContent = agent.id;

    row.append(nameCell, modelCell, idCell);

    if (journalState.currentAgentId === agent.id) {
      row.classList.add('selected');
    }

    row.addEventListener('click', () => {
      selectPlayer(agent);
    });

    body.appendChild(row);
  });
}

function updateSelectedAgentUI(agentId) {
  document.querySelectorAll('.journal-list-table tbody tr').forEach((row) => {
    row.classList.toggle('selected', row.dataset.agentId === agentId);
  });
}

function formatAgentDisplay(agentId) {
  if (!agentId) return '-';

  const meta = journalState.currentAgentMeta;
  if (meta && meta.id === agentId) {
    const name = (meta.name || '').trim();
    if (name && name !== agentId) {
      return `${name} (${agentId})`;
    }
  }

  return agentId;
}

async function selectPlayer(agent) {
  const agentId = typeof agent === 'string' ? agent : agent?.id;
  if (!agentId) return;

  const agentMeta =
    typeof agent === 'object' && agent
      ? agent
      : agentListState.agents.find((candidate) => candidate.id === agentId) || null;

  journalState.currentAgentMeta = agentMeta;

  if (journalState.currentAgentId === agentId) {
    updateSelectedAgentUI(agentId);
    return;
  }
  journalState.currentAgentId = agentId;
  journalState.currentSequenceId = Number.MAX_SAFE_INTEGER;

  updateSelectedAgentUI(agentId);
  await loadLatestJournalEntry();
}

async function loadLatestJournalEntry() {
  if (!journalState.currentAgentId) return;

  try {
    const data = await fetchJournalEntry('down');

    if (data && data.journals && data.journals.length > 0) {
      journalState.isViewing = true;
      journalState.currentSequenceId = data.journals[0].sequenceId;
      displayJournalEntry();
      JournalViewer.setNavigationButtonsEnabled({
        upButtonId: 'journalUpBtn',
        downButtonId: 'journalDownBtn',
        enabled: true,
      });
    } else {
      $('journalAgentId').textContent = formatAgentDisplay(journalState.currentAgentId);
      $('journalSequenceId').textContent = '-';
      $('journalUpdatedAt').textContent = '-';
      $('journalInstructions').textContent = 'No journal entries found for this agent.';
      JournalViewer.setNavigationButtonsEnabled({
        upButtonId: 'journalUpBtn',
        downButtonId: 'journalDownBtn',
        enabled: false,
      });
    }
  } catch (error) {
    console.error('Error loading journal entry:', error);
    $('journalAgentId').textContent = formatAgentDisplay(journalState.currentAgentId);
    $('journalInstructions').textContent = 'Error loading journal entry.';
    JournalViewer.setNavigationButtonsEnabled({
      upButtonId: 'journalUpBtn',
      downButtonId: 'journalDownBtn',
      enabled: false,
    });
  }
}

async function navigateJournal(direction) {
  if (!journalState.currentAgentId) return;

  try {
    const data = await fetchJournalEntry(direction);

    if (data && data.journals && data.journals.length > 0) {
      displayJournalEntry();
    }
  } catch (error) {
    console.error('Error navigating journal:', error);
  }
}

async function fetchJournalEntry(direction) {
  const endpoint = direction === 'up' ? '/playbook/get-journal-by-agent-id-up' : '/playbook/get-journal-by-agent-id-down';

  const dataCurrent = await fetchJson(endpoint, {
    method: 'POST',
    body: JSON.stringify({
      agentId: journalState.currentAgentId,
      sequenceId: journalState.currentSequenceId,
    }),
  });

  if (dataCurrent && dataCurrent.journals && dataCurrent.journals.length > 0) {
    journalState.currentSequenceId = dataCurrent.journals[0].sequenceId;
    journalState.currentEntry = dataCurrent.journals[0];
  }

  const checkbox = $('showDiffCheckbox');
  journalState.showDiff = checkbox ? checkbox.checked : false;

  const dataPrevious = await fetchJson('/playbook/get-journal-by-agent-id-and-sequence', {
    method: 'POST',
    body: JSON.stringify({
      agentId: journalState.currentAgentId,
      sequenceId: journalState.currentSequenceId - 1,
    }),
  });
  if (dataPrevious && dataPrevious.journals && dataPrevious.journals.length > 0) {
    journalState.previousEntry = dataPrevious.journals[0];
  } else {
    journalState.previousEntry = null;
  }

  return dataCurrent;
}

function displayJournalEntry() {
  const entry = journalState.currentEntry;
  // Get all the elements first
  const agentIdEl = $('journalAgentId');
  const seqIdEl = $('journalSequenceId');
  const updatedAtEl = $('journalUpdatedAt');
  const instructionsEl = $('journalInstructions');

  const updatedAt = formatDateTime(entry.updatedAt);

  if (agentIdEl) agentIdEl.textContent = formatAgentDisplay(entry.agentId);
  if (seqIdEl) seqIdEl.textContent = entry.sequenceId;
  if (updatedAtEl) updatedAtEl.textContent = updatedAt;

  JournalViewer.renderTextDiff({
    targetElement: instructionsEl,
    previousText: journalState?.previousEntry?.instructions || '',
    currentText: journalState?.currentEntry?.instructions || '',
    showDiff: journalState.showDiff,
    emptyMessage: 'No instructions available.',
  });
}

// Toggle diff display
function toggleDiff() {
  const checkbox = $('showDiffCheckbox');
  journalState.showDiff = checkbox.checked;

  // If currently viewing a journal entry, refresh the display
  if (journalState.isViewing) {
    displayJournalEntry();
  }
}

// Initialize the UI when the page loads
window.addEventListener('DOMContentLoaded', () => {
  loadAgentList();
});
