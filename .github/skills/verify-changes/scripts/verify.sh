#!/usr/bin/env bash
# Runs the repository's quality gates: backend (Maven) and frontend (npm).
#
#   verify.sh                  backend unit + integration tests, frontend lint + type-check + tests + build
#   verify.sh --quick          skip container-backed backend integration tests (no Docker/Podman needed)
#   verify.sh --backend-only | --frontend-only
#
# Detects JDK 25 (macOS java_home) and a Docker-compatible runtime (Docker, or Podman machine) automatically.
set -euo pipefail

usage() {
  sed -n '2,8p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
QUICK=false
RUN_BACKEND=true
RUN_FRONTEND=true

for arg in "$@"; do
  case "$arg" in
    --quick) QUICK=true ;;
    --backend-only) RUN_FRONTEND=false ;;
    --frontend-only) RUN_BACKEND=false ;;
    -h | --help) usage; exit 0 ;;
    *) echo "Unknown option: $arg" >&2; usage >&2; exit 2 ;;
  esac
done

step() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
fail() { printf '\033[31m%s\033[0m\n' "$*" >&2; exit 1; }

java_major() {
  "$1" -version 2>&1 | awk -F'"' '/version/ { split($2, v, "."); print v[1]; exit }'
}

ensure_jdk() {
  local java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
  local major
  major="$(java_major "$java_bin" 2>/dev/null || true)"
  if [[ -z "$major" || "$major" -lt 25 ]] && [[ -x /usr/libexec/java_home ]]; then
    if JAVA_HOME="$(/usr/libexec/java_home -v 25 2>/dev/null)"; then
      export JAVA_HOME
      major="$(java_major "$JAVA_HOME/bin/java")"
    fi
  fi
  [[ -n "$major" && "$major" -ge 25 ]] || fail "JDK 25+ is required (found: ${major:-none}). Set JAVA_HOME."
  echo "Java $major${JAVA_HOME:+ ($JAVA_HOME)}"
}

ensure_container_runtime() {
  if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    if [[ "${DOCKER_HOST:-}" == *podman* ]]; then
      export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="${TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE:-/var/run/docker.sock}"
    fi
    echo "Container runtime: ${DOCKER_HOST:-default Docker socket}"
    return
  fi
  if command -v podman >/dev/null 2>&1; then
    local socket
    socket="$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}' 2>/dev/null | head -1 || true)"
    if [[ -n "$socket" && -S "$socket" ]]; then
      export DOCKER_HOST="unix://$socket"
      # Ryuk runs inside the Podman VM and must mount the VM-side socket.
      export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="${TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE:-/var/run/docker.sock}"
      echo "Container runtime: Podman ($DOCKER_HOST)"
      return
    fi
  fi
  fail "No Docker-compatible runtime found. Start Docker or 'podman machine start', or run with --quick."
}

run_backend() {
  step "Backend ($([[ $QUICK == true ]] && echo 'unit + architecture tests' || echo 'unit, architecture and integration tests'))"
  ensure_jdk
  cd "$ROOT/backend"
  if [[ $QUICK == true ]]; then
    ./mvnw -B -q test
  else
    ensure_container_runtime
    ./mvnw -B verify
  fi
}

run_frontend() {
  step "Frontend (lint, type-check, tests, build)"
  command -v node >/dev/null 2>&1 || fail "Node.js 22+ is required."
  local node_major
  node_major="$(node -p 'process.versions.node.split(".")[0]')"
  [[ "$node_major" -ge 22 ]] || fail "Node.js 22+ is required (found $node_major)."
  cd "$ROOT/frontend"
  if [[ ! -d node_modules ]]; then
    npm ci --no-audit --no-fund
  fi
  npm run lint
  npm run typecheck
  npm test
  npm run build
}

started=$SECONDS
[[ $RUN_BACKEND == true ]] && run_backend
[[ $RUN_FRONTEND == true ]] && run_frontend
step "All checks passed in $((SECONDS - started))s"
