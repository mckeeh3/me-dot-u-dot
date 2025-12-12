#!/bin/bash

# Script to list Google Gemini models available for API access
# Usage: ./list-gemini-models.sh
# Requires GOOGLE_AI_GEMINI_API_KEY environment variable to be set

if [ -z "$GOOGLE_AI_GEMINI_API_KEY" ]; then
    echo "Error: GOOGLE_AI_GEMINI_API_KEY environment variable is not set"
    echo "Please set it with: export GOOGLE_AI_GEMINI_API_KEY=your-api-key"
    exit 1
fi

echo "Fetching available Google Gemini models..."
echo ""

# Make API call to list models
RESPONSE=$(curl -s -X GET \
    -H "Content-Type: application/json" \
    "https://generativelanguage.googleapis.com/v1beta/models?key=$GOOGLE_AI_GEMINI_API_KEY")

# Check if curl was successful
if [ $? -ne 0 ]; then
    echo "Error: Failed to connect to Google Gemini API"
    exit 1
fi

# Check if response contains error
if echo "$RESPONSE" | grep -q '"error"'; then
    echo "Error from Google Gemini API:"
    echo "$RESPONSE" | grep -o '"message":"[^"]*"' | sed 's/"message":"\(.*\)"/\1/'
    exit 1
fi

# Format output - use jq if available, otherwise show raw JSON
if command -v jq &> /dev/null; then
    echo "$RESPONSE" | jq -r '.models | sort_by(.name) | .[] | "\(.name) (\(.displayName // "N/A"))"'
    echo ""
    echo "Total models: $(echo "$RESPONSE" | jq '.models | length')"
else
    echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"
fi
