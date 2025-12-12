#!/bin/bash

# Script to list Anthropic Claude models available for API access
# Usage: ./list-anthropic-models.sh
# Requires ANTHROPIC_API_KEY environment variable to be set

if [ -z "$ANTHROPIC_API_KEY" ]; then
    echo "Error: ANTHROPIC_API_KEY environment variable is not set"
    echo "Please set it with: export ANTHROPIC_API_KEY=your-api-key"
    exit 1
fi

echo "Fetching available Anthropic Claude models..."
echo ""

# Make API call to list models
RESPONSE=$(curl -s -X GET \
    -H "x-api-key: $ANTHROPIC_API_KEY" \
    -H "anthropic-version: 2023-06-01" \
    -H "Content-Type: application/json" \
    "https://api.anthropic.com/v1/models")

# Check if curl was successful
if [ $? -ne 0 ]; then
    echo "Error: Failed to connect to Anthropic API"
    exit 1
fi

# Check if response contains error
if echo "$RESPONSE" | grep -q '"error"'; then
    echo "Error from Anthropic API:"
    echo "$RESPONSE" | grep -o '"message":"[^"]*"' | sed 's/"message":"\(.*\)"/\1/'
    exit 1
fi

# Format output - use jq if available, otherwise show raw JSON
if command -v jq &> /dev/null; then
    echo "$RESPONSE" | jq -r '.data | sort_by(.id) | .[] | "\(.id)"'
    echo ""
    echo "Total models: $(echo "$RESPONSE" | jq '.data | length')"
else
    echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"
fi
