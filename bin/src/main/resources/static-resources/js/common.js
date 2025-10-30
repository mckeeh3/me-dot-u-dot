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

function resolveDropdownElements(id) {
  // Try explicit prefix pattern: `${id}-menu` / `${id}-btn`
  let menu = $(`${id}-menu`);
  let btn = $(`${id}-btn`);

  // If not found, treat `id` as the actual menu id
  if (!menu) {
    const candidate = $(id);
    if (candidate) {
      if (candidate.classList && candidate.classList.contains('dd-menu')) {
        menu = candidate;
        const container = candidate.closest('.dd');
        if (container) {
          btn = container.querySelector('.dd-toggle');
        }
      } else if (candidate.classList && candidate.classList.contains('dd')) {
        // `id` refers to the container; find internal menu/button
        menu = candidate.querySelector('.dd-menu');
        btn = candidate.querySelector('.dd-toggle');
      }
    }
  }

  return { menu, btn };
}

function ddToggle(id) {
  const { menu } = resolveDropdownElements(id);
  if (!menu) return;

  const isVisible = menu.style.display === 'block';
  menu.style.display = isVisible ? 'none' : 'block';

  if (!isVisible) {
    // Create and store the handler
    dropdownHandlers[id] = (e) => ddClose(id, e);
    document.addEventListener('click', dropdownHandlers[id]);
  }
}

function ddClose(id, event) {
  const { menu, btn } = resolveDropdownElements(id);
  if (!menu) return;

  const target = event?.target;
  const clickInsideMenu = target && menu.contains(target);
  const clickOnBtn = btn && target && btn.contains(target);
  if (!clickInsideMenu && !clickOnBtn) {
    menu.style.display = 'none';
    // Remove the stored handler
    if (dropdownHandlers[id]) {
      document.removeEventListener('click', dropdownHandlers[id]);
      delete dropdownHandlers[id];
    }
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
