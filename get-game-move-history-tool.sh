#!/bin/bash

# Script to get game move history tool using the Me-Dot-U-Dot API
# Usage: ./get-game-move-history-tool.sh <gameId>

if [ $# -eq 0 ]; then
    echo "Usage: $0 <gameId>"
    echo "Example: $0 game-123"
    exit 1
fi

GAME_ID="$1"

# Default to localhost:9000 if AKKA_RUNTIME_HTTP_INTERFACE is not set
HOST="${AKKA_RUNTIME_HTTP_INTERFACE:-localhost:9000}"

curl -X GET "http://$HOST/game/get-game-move-history-tool/$GAME_ID" \
  -H "Accept: application/json" \
  -s

echo ""

