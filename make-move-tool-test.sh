#!/bin/bash

# Make Move Tool Test Script
# Usage: ./make-move-tool-test.sh <gameId> <agentId> <squareId>
# Example: ./make-move-tool-test.sh "game-123" "agent-456" "C3"

if [ $# -ne 3 ]; then
    echo "Usage: $0 <gameId> <agentId> <squareId>"
    echo "Example: $0 'game-123' 'agent-456' 'C3'"
    exit 1
fi

GAME_ID="$1"
AGENT_ID="$2"
SQUARE_ID="$3"

# Default to localhost:9000 if AKKA_RUNTIME_HTTP_INTERFACE is not set
HOST="${AKKA_RUNTIME_HTTP_INTERFACE:-localhost:9000}"

curl -X GET "http://$HOST/game/make-move-tool-test/$GAME_ID/$AGENT_ID/$SQUARE_ID" \
  -H "Accept: application/json" \
  -w "\nHTTP Status: %{http_code}\n" \
  -s

