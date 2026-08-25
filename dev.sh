#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ ! -d "$ROOT_DIR/frontend/node_modules" ]]; then
  npm --prefix "$ROOT_DIR/frontend" install
fi

"$ROOT_DIR/mvnw" spring-boot:run &
BACKEND_PID=$!

npm --prefix "$ROOT_DIR/frontend" run dev -- --host 0.0.0.0 &
FRONTEND_PID=$!

cleanup() {
  kill "$FRONTEND_PID" "$BACKEND_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo "ResearchFlow frontend: http://localhost:5173"
echo "ResearchFlow backend:  http://localhost:8080"
wait
