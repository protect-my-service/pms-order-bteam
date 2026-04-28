#!/usr/bin/env bash
#
# nginx 컨테이너 안에서 target 색상의 readiness endpoint를 폴링.
# (앱 컨테이너는 expose만 했으므로 호스트에서 직접 접근 불가 — nginx 네트워크 활용)
#
# 인자:
#   $1: target color (blue | green)
#   $2: target port (8087 | 8088)
#   $3: health path (e.g. /actuator/health/readiness)
#   $4: timeout seconds (default 90)

set -euo pipefail

target="${1:?target color required}"
port="${2:?target port required}"
path="${3:?health path required}"
timeout="${4:-90}"

APP_DIR="${APP_DIR:-${HOME}/app}"
COMPOSE_FILE="${COMPOSE_FILE:-${APP_DIR}/docker-compose.yml}"
COMPOSE="docker compose -f ${COMPOSE_FILE}"

deadline=$(( $(date +%s) + timeout ))
attempt=0

while (( $(date +%s) < deadline )); do
    attempt=$(( attempt + 1 ))
    if $COMPOSE exec -T nginx wget -q -O- --timeout=3 "http://${target}:${port}${path}" >/dev/null 2>&1; then
        echo "[health-check] ${target}:${port}${path} OK after ${attempt} attempts" >&2
        exit 0
    fi
    sleep 1
done

echo "[health-check] ${target}:${port}${path} FAILED after ${timeout}s (${attempt} attempts)" >&2
exit 1
