#!/bin/bash

# Script to get the leader board using the Me-Dot-U-Dot API
# Usage: ./leader-board.sh <limit> <offset>
# Example: ./leader-board.sh 10 0

if [ $# -ne 2 ]; then
    echo "Usage: $0 <limit> <offset>"
    echo "Example: $0 10 0"
    echo "  limit:  Maximum number of players to return"
    echo "  offset: Number of players to skip (for pagination)"
    exit 1
fi

LIMIT="$1"
OFFSET="$2"
BASE_URL="http://localhost:9000"

curl -X POST \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d "{\"limit\":$LIMIT,\"offset\":$OFFSET}" \
  "$BASE_URL/player-games/get-leader-board"

echo ""
