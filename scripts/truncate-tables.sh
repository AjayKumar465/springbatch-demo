#!/usr/bin/env bash
# Truncate all tables in the batchdb database.
# Usage: ./scripts/truncate-tables.sh
# Or:    ./scripts/truncate-tables.sh -d mydb -U myuser

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PSQL_OPTS="${@:--U postgres -d batchdb}"

psql $PSQL_OPTS -f "$SCRIPT_DIR/truncate-tables.sql"

echo "All tables truncated."
