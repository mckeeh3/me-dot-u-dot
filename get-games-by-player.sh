#!/bin/bash

# Script to get games by player ID with pagination using the Me-Dot-U-Dot API
# Usage: ./get-games-by-player.sh <playerId> <limit> <offset>
# Example: ./get-games-by-player.sh player123 10 0

if [ $# -ne 3 ]; then
    echo "Usage: $0 <playerId> <limit> <offset>"
    echo "Example: $0 player123 10 0"
    echo "  playerId: The ID of the player to get games for"
    echo "  limit:    Maximum number of games to return"
    echo "  offset:   Number of games to skip (for pagination)"
    exit 1
fi

PLAYER_ID="$1"
LIMIT="$2"
OFFSET="$3"
BASE_URL="http://localhost:9000"

curl -X POST \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d "{\"playerId\":\"$PLAYER_ID\",\"limit\":$LIMIT,\"offset\":$OFFSET}" \
  "$BASE_URL/game/get-games-by-player-id-paged"
