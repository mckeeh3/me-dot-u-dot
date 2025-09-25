(function () {
  // Global variable to track the square popup
  let squarePopup = null;

  function squareDataFromGameState(squareEl, gameState) {
    const squareId = squareEl.dataset.squareId;
    const playerId = gameState.board.squares.find((square) => square.squareId === squareId)?.playerId;
    const isPlayer1 = playerId === gameState.player1Status.player.id;
    const moveCounts = calculateMoveCounts(gameState);
    const moveData = moveCounts.find((move) => move.squareId === squareId);
    const gameMove = moveData ? moveData.gameMoves : '';
    const playerMove = moveData ? (isPlayer1 ? moveData.p1Moves : moveData.p2Moves) : '';
    const thinkTime = moveData ? (isPlayer1 ? moveData.p1ThinkMs : moveData.p2ThinkMs) : '';

    return {
      squareId,
      isPlayer1,
      gameMove,
      playerMove,
      thinkTime,
    };
  }

  function squareDataFromReplayState(squareEl, replayState, moveData) {
    const squareId = squareEl.dataset.squareId;
    const playerId = moveData.playerId;
    const isPlayer1 = playerId === replayState.gameState.player1Status.player.id;
    const gameMove = moveData.gameMove;
    const playerMove = moveData.playerMove;
    const thinkTime = moveData.thinkTime;

    return {
      squareId,
      isPlayer1,
      gameMove,
      playerMove,
      thinkTime,
    };
  }

  function squarePopupSetup(square, squareData) {
    square.addEventListener('mouseenter', (event) => {
      // Store mouse position for popup positioning
      square._mouseX = event.clientX;
      square._mouseY = event.clientY;

      square._hoverTimeout = setTimeout(() => {
        showSquarePopup(square, squareData);
        square._hoverTimeout = null;
      }, 1000);
    });

    square.addEventListener('mouseleave', () => {
      if (square._hoverTimeout) {
        clearTimeout(square._hoverTimeout);
        square._hoverTimeout = null;
      }
      hideSquarePopup();
    });

    // Track mouse movement for popup positioning
    square.addEventListener('mousemove', (event) => {
      square._mouseX = event.clientX;
      square._mouseY = event.clientY;
    });
  }

  function showSquarePopup(square, squareData) {
    // Hide any existing popup
    hideSquarePopup();

    // Create the square element
    squarePopup = document.createElement('div');
    squarePopup.className = `square-popup ${squareData.isPlayer1 ? 'player1' : 'player2'}`;

    // Create the same 3-layer structure as the original square
    squarePopup.innerHTML = `
    <div class="square-layer square-layer-top">
      <span class="square-id">${squareData.squareId}</span>
      <span class="game-move-count">${squareData.gameMove}</span>
    </div>
    <div class="square-layer square-layer-middle">
      <span class="player-square ${squareData.isPlayer1 ? 'player1' : 'player2'}">●</span>
    </div>
    <div class="square-layer square-layer-bottom">
      <span class="player-think-time">${squareData.thinkTime}</span>
      <span class="player-move-count">${squareData.playerMove}</span>
    </div>
  `;

    // Position the popup near the mouse but keep it visible in the window
    const mouseX = square._mouseX || 0;
    const mouseY = square._mouseY || 0;
    const popupSize = Math.min(window.innerWidth * 0.1, window.innerHeight * 0.1);
    const offset = 20;

    let left = mouseX + offset;
    let top = mouseY + offset;

    // Adjust position to keep popup in viewport
    if (left + popupSize > window.innerWidth) {
      left = mouseX - popupSize - offset;
    }
    if (top + popupSize > window.innerHeight) {
      top = mouseY - popupSize - offset;
    }
    if (left < 0) left = offset;
    if (top < 0) top = offset;

    squarePopup.style.left = `${left}px`;
    squarePopup.style.top = `${top}px`;

    // Add to DOM and show with animation
    document.body.appendChild(squarePopup);

    // Trigger animation after a brief delay to ensure proper rendering
    setTimeout(() => {
      squarePopup.classList.add('show');
    }, 10);
  }

  function hideSquarePopup() {
    if (squarePopup) {
      squarePopup.classList.remove('show');
      setTimeout(() => {
        if (squarePopup && squarePopup.parentNode) {
          squarePopup.parentNode.removeChild(squarePopup);
        }
        squarePopup = null;
      }, 200); // Match the CSS transition duration
    }
  }

  window.SquarePopup = {
    squareDataFromGameState,
    squareDataFromReplayState,
    squarePopupSetup,
  };
})();
