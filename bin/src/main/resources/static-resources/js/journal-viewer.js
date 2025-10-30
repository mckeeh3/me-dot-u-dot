(function () {
  function clearNode(node) {
    while (node.firstChild) {
      node.removeChild(node.firstChild);
    }
  }

  function renderTextDiff({ targetElement, previousText = '', currentText = '', showDiff = false, emptyMessage = 'No content available.' }) {
    if (!targetElement) return;

    const fragment = document.createDocumentFragment();
    const previous = previousText || '';
    const current = currentText || '';

    if (showDiff && typeof Diff !== 'undefined') {
      const diff = Diff.diffWords(previous, current);
      diff.forEach((part) => {
        const span = document.createElement('span');
        span.style.color = part.added ? 'green' : part.removed ? 'red' : 'lightgrey';
        span.appendChild(document.createTextNode(part.value));
        fragment.appendChild(span);
      });
    } else {
      const span = document.createElement('span');
      span.textContent = current || emptyMessage;
      fragment.appendChild(span);
    }

    clearNode(targetElement);
    targetElement.appendChild(fragment);
  }

  function setNavigationButtonsEnabled({ upButtonId, downButtonId, diffCheckboxId, enabled }) {
    const upButton = typeof upButtonId === 'string' ? $(upButtonId) : upButtonId;
    const downButton = typeof downButtonId === 'string' ? $(downButtonId) : downButtonId;
    const diffCheckbox = typeof diffCheckboxId === 'string' ? $(diffCheckboxId) : diffCheckboxId;
    const isEnabled = Boolean(enabled);

    if (upButton) upButton.disabled = !isEnabled;
    if (downButton) downButton.disabled = !isEnabled;
    if (diffCheckbox) diffCheckbox.disabled = !isEnabled;
  }

  window.JournalViewer = {
    renderTextDiff,
    setNavigationButtonsEnabled,
  };
})();
