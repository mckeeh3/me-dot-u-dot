// Utility function for getting elements by ID
const $ = (id) => document.getElementById(id);

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

// Dropdown functions
// Store dropdown click handler for proper removal
const dropdownHandlers = {};

function ddToggle(id) {
  const menu = $(`${id}-menu`);
  const isVisible = menu.style.display === 'block';
  menu.style.display = isVisible ? 'none' : 'block';

  if (!isVisible) {
    // Create and store the handler
    dropdownHandlers[id] = (e) => ddClose(id, e);
    document.addEventListener('click', dropdownHandlers[id]);
  }
}

function ddClose(id, event) {
  const menu = $(`${id}-menu`);
  const btn = $(`${id}-btn`);

  if (!menu.contains(event.target) && !btn.contains(event.target)) {
    menu.style.display = 'none';
    // Remove the stored handler
    document.removeEventListener('click', dropdownHandlers[id]);
    delete dropdownHandlers[id];
  }
}

// Journal state
const journalState = {
  isViewing: false,
  currentAgentId: null,
  currentSequenceId: Number.MAX_SAFE_INTEGER,
};

async function populatePlayerMenu() {
  const menu = $('player-dd-menu');
  const btn = $('player-dd-btn');
  menu.innerHTML = '';

  try {
    const res = await fetch('/player/get-players', {
      headers: { Accept: 'application/json' },
    });
    const response = await res.json();
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
    const res = await fetch('/game/get-journal-by-agent-id-down', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify({
        agentId: journalState.currentAgentId,
        sequenceId: journalState.currentSequenceId,
      }),
    });

    if (res.ok) {
      const data = await res.json();
      if (data && data.journals && data.journals.length > 0) {
        journalState.isViewing = true;
        displayJournalEntry(data.journals[0]);
        journalState.currentSequenceId = data.journals[0].sequenceId;
        enableNavigationButtons();
      } else {
        $('journalAgentId').textContent = journalState.currentAgentId;
        $('journalSequenceId').textContent = '-';
        $('journalUpdatedAt').textContent = '-';
        $('journalInstructions').textContent = 'No journal entries found for this agent.';
        disableNavigationButtons();
      }
    }
  } catch (error) {
    console.error('Error loading journal entry:', error);
    $('journalInstructions').textContent = 'Error loading journal entry.';
    disableNavigationButtons();
  }
}

async function navigateJournal(direction) {
  if (!journalState.currentAgentId) return;

  const endpoint = direction === 'up' ? '/game/get-journal-by-agent-id-up' : '/game/get-journal-by-agent-id-down';

  try {
    const res = await fetch(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify({
        agentId: journalState.currentAgentId,
        sequenceId: journalState.currentSequenceId,
      }),
    });

    if (res.ok) {
      const data = await res.json();
      if (data && data.journals && data.journals.length > 0) {
        displayJournalEntry(data.journals[0]);
        journalState.currentSequenceId = data.journals[0].sequenceId;
      }
    }
  } catch (error) {
    console.error('Error navigating journal:', error);
  }
}

function displayJournalEntry(entry) {
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
  const updatedAt = entry.updatedAt ? new Date(entry.updatedAt).toLocaleString() : '-';

  // Update the elements
  if (agentIdEl) agentIdEl.textContent = entry.agentId;
  if (seqIdEl) seqIdEl.textContent = entry.sequenceId;
  if (updatedAtEl) updatedAtEl.textContent = updatedAt;
  if (instructionsEl) instructionsEl.textContent = entry.instructions || 'No instructions available.';
}

function enableNavigationButtons() {
  $('journalUpBtn').disabled = false;
  $('journalDownBtn').disabled = false;
}

function disableNavigationButtons() {
  $('journalUpBtn').disabled = true;
  $('journalDownBtn').disabled = true;
}

// Initialize the UI when the page loads
window.addEventListener('DOMContentLoaded', () => {
  populatePlayerMenu();
});
