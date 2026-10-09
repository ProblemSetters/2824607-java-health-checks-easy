#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

cat > "$SCRIPT_DIR/data/health.json" <<'EOF'
{
  "services": [
    {"service":"api","status":"ok","fails_in_row":0,"passes_in_row":9,"muted":false,"alerts":2},
    {"service":"search","status":"alerting","fails_in_row":5,"passes_in_row":0,"muted":true,"alerts":4},
    {"service":"billing-worker","status":"ok","fails_in_row":2,"passes_in_row":0,"muted":false,"alerts":1},
    {"service":"checkout","status":"ok","fails_in_row":0,"passes_in_row":1,"muted":false,"alerts":0},
    {"service":"image-resize","status":"alerting","fails_in_row":0,"passes_in_row":1,"muted":false,"alerts":3},
    {"service":"notify","status":"ok","fails_in_row":1,"passes_in_row":0,"muted":false,"alerts":0},
    {"service":"report-export","status":"ok","fails_in_row":0,"passes_in_row":14,"muted":true,"alerts":6},
    {"service":"auth","status":"alerting","fails_in_row":7,"passes_in_row":0,"muted":false,"alerts":5},
    {"service":"cache-warmer","status":"ok","fails_in_row":0,"passes_in_row":2,"muted":false,"alerts":0},
    {"service":"pdf-render","status":"ok","fails_in_row":0,"passes_in_row":3,"muted":false,"alerts":1}
  ]
}
EOF

echo "Restored data/health.json to its initial state"
