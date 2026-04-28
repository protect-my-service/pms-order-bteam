#!/usr/bin/env bash
#
# 인스턴스 내부 Blue/Green 스왑.
# 호출자(bteam-jenkins)가 동기화한 ~/app 아래에서 실행되며,
# ECR_REPO / IMAGE_TAG / SPRING_PROFILES_ACTIVE 환경변수가 export 된 상태여야 함.
#
# 동작:
#   1) 현재 active color를 state 파일 → nginx upstream → 기본값(green) 순서로 자가복구 추론
#   2) 반대 색상(target)을 새 이미지로 기동 후 readiness 폴링
#   3) nginx upstream을 target으로 스왑하고 reload
#      (nginx -t 실패 시 자동으로 active로 되돌리고 target stop → exit 1)
#   4) state 파일 갱신
#   5) old color는 stop 하지 않음 — ALB healthy 검증 후 호출자가 stop-old-color.sh 호출
#
# 표준 출력 마지막 줄: 직전 active color (호출자가 stop-old-color.sh 인자로 사용)

set -euo pipefail

HEALTH_PATH="${1:-/actuator/health/readiness}"
APP_DIR="${APP_DIR:-${HOME}/app}"
COMPOSE_FILE="${COMPOSE_FILE:-${APP_DIR}/docker-compose.yml}"
COMPOSE="docker compose -f ${COMPOSE_FILE}"
STATE_DIR="${APP_DIR}/state"
STATE_FILE="${STATE_DIR}/active_color"
UPSTREAM_FILE="${APP_DIR}/nginx/conf.d/upstream.conf"

: "${ECR_REPO:?ECR_REPO env required}"
: "${IMAGE_TAG:?IMAGE_TAG env required}"

log() { echo "[deploy] $*" >&2; }

detect_active() {
    if [[ -f "$STATE_FILE" ]]; then
        cat "$STATE_FILE"
    elif grep -q 'server blue:' "$UPSTREAM_FILE" 2>/dev/null; then
        echo blue
    elif grep -q 'server green:' "$UPSTREAM_FILE" 2>/dev/null; then
        echo green
    else
        echo green
    fi
}

active="$(detect_active)"
if [[ "$active" == "blue" ]]; then
    target="green"; target_port=8088
else
    target="blue"; target_port=8087
fi

log "active=$active target=$target port=$target_port image=${ECR_REPO}:${IMAGE_TAG}"

cd "$APP_DIR"

log "pulling target image"
if ! $COMPOSE pull "$target"; then
    log "WARN: pull failed (offline/local-only?). Falling back to local image."
    if ! docker image inspect "${ECR_REPO}:${IMAGE_TAG}" >/dev/null 2>&1; then
        log "ERROR: image ${ECR_REPO}:${IMAGE_TAG} not present locally either"
        exit 1
    fi
fi

log "starting target container"
$COMPOSE up -d --no-deps "$target"

log "waiting for $target readiness"
"${APP_DIR}/scripts/health-check.sh" "$target" "$target_port" "$HEALTH_PATH" 90

log "swapping nginx upstream to $target"
cp "${APP_DIR}/nginx/templates/upstream-${target}.conf" "$UPSTREAM_FILE"

if ! $COMPOSE exec -T nginx nginx -t; then
    log "nginx -t FAILED — reverting upstream to $active and stopping $target"
    cp "${APP_DIR}/nginx/templates/upstream-${active}.conf" "$UPSTREAM_FILE"
    $COMPOSE stop "$target" || true
    exit 1
fi

$COMPOSE exec -T nginx nginx -s reload

mkdir -p "$STATE_DIR"
echo "$target" > "$STATE_FILE"
log "switched to $target — old color $active still running for safe rollback"

# stdout 마지막 줄 = old color
echo "$active"
