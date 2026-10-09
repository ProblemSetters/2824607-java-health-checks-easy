#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

mkdir -p data

mvn -B -ntp -DskipTests compile 2>&1

echo ""
echo "State file: data/health.json"
echo ""
echo "Usage:"
echo "  ./health check <service> <up|down>"
echo "  ./health mute <service>"
echo "  ./health unmute <service>"
echo ""
echo "Reset:"
echo "  bash reset.sh"
