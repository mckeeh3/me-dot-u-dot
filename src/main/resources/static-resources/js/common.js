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

// Dropdown handling
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

// Common fetch utilities
async function fetchJson(url, options = {}) {
  try {
    const defaultHeaders = {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    };

    const response = await fetch(url, {
      ...options,
      headers: {
        ...defaultHeaders,
        ...options.headers,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  } catch (error) {
    console.error('Fetch error:', error);
    throw error;
  }
}

// Date formatting utility
function formatDateTime(date) {
  if (!date) return '-';
  return new Date(date).toLocaleString();
}

// Time formatting utility
function formatTime(seconds) {
  if (!seconds) return '0s';
  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = seconds % 60;
  return minutes > 0 ? `${minutes}m${remainingSeconds}s` : `${remainingSeconds}s`;
}

// Sound utilities
function playSound(soundName) {
  const audio = new Audio(`/sounds/${soundName}.mp3`);
  audio.play().catch((e) => console.warn('Sound playback failed:', e));
}

// Export common functions
window.$ = $;
window.toggleMenu = toggleMenu;
window.ddToggle = ddToggle;
window.fetchJson = fetchJson;
window.formatDateTime = formatDateTime;
window.formatTime = formatTime;
window.playSound = playSound;
