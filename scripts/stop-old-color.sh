#!/usr/bin/env bash
#
# 직전 active 색상 컨테이너를 정지.
# 반드시 ALB healthy 검증 이후에만 호출되어야 함 (롤백 윈도우 보장).

set -euo pipefail

old="${1:?old color required (blue|green)}"
APP_DIR="${APP_DIR:-${HOME}/app}"
COMPOSE_FILE="${COMPOSE_FILE:-${APP_DIR}/docker-compose.yml}"
COMPOSE="docker compose -f ${COMPOSE_FILE}"

if [[ "$old" != "blue" && "$old" != "green" ]]; then
    echo "[stop-old-color] invalid color: $old" >&2
    exit 1
fi

echo "[stop-old-color] stopping $old" >&2
$COMPOSE stop "$old" || true
echo "[stop-old-color] done" >&2
