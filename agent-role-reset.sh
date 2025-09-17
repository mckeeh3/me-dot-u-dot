#!/bin/bash

# Reset Agent Role Script
# Usage: ./agent-role-reset.sh <agentId>
# Example: ./agent-role-reset.sh "my-agent-123"

if [ $# -ne 1 ]; then
    echo "Usage: $0 <agentId>"
    echo "Example: $0 'my-agent-123'"
    exit 1
fi

AGENT_ID="$1"

# Default to localhost:9000 if AKKA_RUNTIME_HTTP_INTERFACE is not set
HOST="${AKKA_RUNTIME_HTTP_INTERFACE:-localhost:9000}"

echo "Resetting agent role for agent: $AGENT_ID"
echo "Host: $HOST"
echo

curl -X POST "http://$HOST/agent-role/reset-agent-role" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d "{
    \"agentId\": \"$AGENT_ID\"
  }" \
  -w "\nHTTP Status: %{http_code}\n" \
  -s

echo
echo "Agent role reset request completed."
