#!/bin/bash

# Script to cancel a game using the Me-Dot-U-Dot API
# Usage: ./cancel-game.sh <gameId>

if [ $# -eq 0 ]; then
    echo "Usage: $0 <gameId>"
    echo "Example: $0 game-123"
    exit 1
fi

GAME_ID="$1"
BASE_URL="http://localhost:9000"

echo "Canceling game: $GAME_ID"

curl -X POST \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d "{\"gameId\":\"$GAME_ID\"}" \
  "$BASE_URL/game/cancel-game"

echo ""
echo "Game cancellation request sent."
