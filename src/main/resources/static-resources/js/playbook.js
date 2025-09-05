// Journal state
const journalState = {
  isViewing: false,
  currentAgentId: null,
  currentSequenceId: Number.MAX_SAFE_INTEGER,
  showDiff: false,
};

async function populatePlayerMenu() {
  const menu = $('player-dd-menu');
  const btn = $('player-dd-btn');
  menu.innerHTML = '';

  try {
    const response = await fetchJson('/player/get-players');
    const players = response.players || [];

    // Filter to show only agent players
    const agentPlayers = players.filter((p) => p.type.toLowerCase() === 'agent');

    if (!agentPlayers.length) {
      const empty = document.createElement('div');
      empty.className = 'dd-item';
      empty.textContent = 'No agent players available';
      menu.appendChild(empty);
      btn.textContent = '— Select a player —';
      return;
    }

    agentPlayers.forEach((p) => {
      const item = document.createElement('div');
      item.className = 'dd-item';
      item.textContent = `${p.name} (${p.model || 'No model'})`;
      item.onclick = (e) => {
        e.stopPropagation();
        btn.textContent = item.textContent;
        btn.dataset.playerId = p.id;
        // Close dropdown menu
        const menu = $('player-dd-menu');
        if (menu) {
          menu.style.display = 'none';
        }
        // Remove click handler
        document.removeEventListener('click', dropdownHandlers['player-dd']);
        delete dropdownHandlers['player-dd'];
        // Select the player
        selectPlayer(p.id);
      };
      menu.appendChild(item);
    });
  } catch (error) {
    console.error('Error fetching players:', error);
    const item = document.createElement('div');
    item.className = 'dd-item';
    item.textContent = 'Error loading players';
    item.style.opacity = '0.5';
    menu.appendChild(item);
  }
}

async function selectPlayer(agentId) {
  journalState.currentAgentId = agentId;
  journalState.currentSequenceId = Number.MAX_SAFE_INTEGER;
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
      enableNavigationButtons();
    } else {
      $('journalAgentId').textContent = journalState.currentAgentId;
      $('journalSequenceId').textContent = '-';
      $('journalUpdatedAt').textContent = '-';
      $('journalInstructions').textContent = 'No journal entries found for this agent.';
      disableNavigationButtons();
    }
  } catch (error) {
    console.error('Error loading journal entry:', error);
    $('journalInstructions').textContent = 'Error loading journal entry.';
    disableNavigationButtons();
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
  // Show the journal viewer
  const journalViewer = document.querySelector('.journal-viewer');
  if (journalViewer) {
    journalViewer.style.display = 'flex';
  }

  // Get all the elements first
  const agentIdEl = $('journalAgentId');
  const seqIdEl = $('journalSequenceId');
  const updatedAtEl = $('journalUpdatedAt');
  const instructionsEl = $('journalInstructions');

  // Format the date if available
  const updatedAt = formatDateTime(entry.updatedAt);

  // Update the elements
  if (agentIdEl) agentIdEl.textContent = entry.agentId;
  if (seqIdEl) seqIdEl.textContent = entry.sequenceId;
  if (updatedAtEl) updatedAtEl.textContent = updatedAt;

  diffJournalEntry(instructionsEl);
}

function diffJournalEntry(instructionsEl) {
  const diffFragment = document.createDocumentFragment();
  if (journalState.showDiff) {
    const oldInstructions = journalState?.previousEntry?.instructions || '';
    const newInstructions = journalState?.currentEntry?.instructions || '';
    const diff = Diff.diffWords(oldInstructions, newInstructions);

    diff.forEach((part) => {
      const diffSpan = document.createElement('span');
      const color = part.added ? 'green' : part.removed ? 'red' : 'lightgrey';
      diffSpan.style.color = color;
      diffSpan.appendChild(document.createTextNode(part.value));
      diffFragment.appendChild(diffSpan);
    });
  } else {
    const instructionsSpan = document.createElement('span');
    instructionsSpan.textContent = journalState?.currentEntry?.instructions || 'No instructions available.';
    diffFragment.appendChild(instructionsSpan);
  }

  while (instructionsEl.firstChild) {
    instructionsEl.removeChild(instructionsEl.firstChild);
  }

  instructionsEl.appendChild(diffFragment);
}

function enableNavigationButtons() {
  $('journalUpBtn').disabled = false;
  $('journalDownBtn').disabled = false;
}

function disableNavigationButtons() {
  $('journalUpBtn').disabled = true;
  $('journalDownBtn').disabled = true;
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
  populatePlayerMenu();
});
