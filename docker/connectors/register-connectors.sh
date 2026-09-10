#!/bin/sh
set -e

CONNECT_URL="http://kafka-connect:8083"

echo "Registering Debezium connectors..."
for file in /connectors/*.json; do
    name=$(basename "$file" .json)
    echo "  -> $name"
    curl -s -X POST \
        -H "Content-Type: application/json" \
        --data "@$file" \
        "$CONNECT_URL/connectors"
    echo ""
done
echo "Done."
