(function () {
  const root = document.documentElement;

  const updateViewportMetrics = () => {
    const vh = window.innerHeight * 0.01;
    root.style.setProperty('--cn-vh', `${vh}px`);
    const orientation = window.innerWidth >= window.innerHeight ? 'landscape' : 'portrait';
    root.setAttribute('data-orientation', orientation);
  };

  window.addEventListener('resize', updateViewportMetrics, { passive: true });
  window.addEventListener('orientationchange', updateViewportMetrics, { passive: true });
  window.addEventListener('DOMContentLoaded', updateViewportMetrics, { once: true });
  updateViewportMetrics();
})();
