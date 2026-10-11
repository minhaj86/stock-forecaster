#!/usr/bin/env bash
# End-to-end smoke test of a running StockLens stack through its public entry point.
#
#   smoke-test.sh                          # Docker Compose stack: http://localhost:3000 (nginx → backend)
#   smoke-test.sh http://localhost:5173    # Vite dev server (proxies /api to the backend)
#   smoke-test.sh http://localhost:8080    # backend directly (no SPA checks)
#
# Registers a throw-away user, adds a stock to the starter watchlist, waits for market data, reads the dashboard and
# analytics, rotates the session through the refresh cookie and signs out. Requires curl and python3.
set -euo pipefail

BASE="${1:-http://localhost:3000}"
API="$BASE/api/v1"
SYMBOL="${SMOKE_SYMBOL:-MSFT}"
TIMEOUT_SECONDS="${SMOKE_TIMEOUT:-60}"

for tool in curl python3; do
  command -v "$tool" >/dev/null 2>&1 || { echo "$tool is required" >&2; exit 2; }
done

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cookies="$work/cookies.txt"

pass() { printf '  \033[32m✓\033[0m %s\n' "$*"; }
die() { printf '  \033[31m✗ %s\033[0m\n' "$*" >&2; exit 1; }

# json FILE EXPRESSION — evaluates a Python expression against the parsed JSON document "d".
json() { python3 -c "import json,sys; d=json.load(open(sys.argv[1])); print($2)" "$1"; }

# request METHOD PATH [BODY] — writes the body to $work/body and prints the HTTP status.
request() {
  local method="$1" path="$2" body="${3:-}"
  local args=(-s -o "$work/body" -w '%{http_code}' -X "$method" -b "$cookies" -c "$cookies")
  [[ -n "${TOKEN:-}" ]] && args+=(-H "Authorization: Bearer $TOKEN")
  [[ -n "$body" ]] && args+=(-H 'Content-Type: application/json' -d "$body")
  curl "${args[@]}" "$API$path"
}

echo "Smoke-testing $BASE"

status="$(curl -s -o "$work/index.html" -w '%{http_code}' "$BASE/")"
if [[ "$status" == 200 ]] && grep -q '<div id="root">' "$work/index.html"; then
  pass "single-page app served"
  status="$(curl -s -o /dev/null -w '%{http_code}' "$BASE/app/dashboard")"
  [[ "$status" == 200 ]] || die "client-side route /app/dashboard returned $status (SPA fallback missing?)"
  pass "client-side routes fall back to index.html"
else
  pass "no SPA at $BASE (API-only target)"
fi

email="smoke-$(date +%s)-$RANDOM@example.com"
status="$(request POST /auth/register "{\"email\":\"$email\",\"password\":\"correct-horse-battery-staple\",\"displayName\":\"Smoke Test\"}")"
[[ "$status" == 201 ]] || die "registration returned $status: $(cat "$work/body")"
TOKEN="$(json "$work/body" "d['accessToken']")"
grep -q sf_refresh_token "$cookies" || die "no refresh-token cookie was set"
pass "registered $email (access token + HttpOnly refresh cookie)"

status="$(request GET /watchlists)"
[[ "$status" == 200 ]] || die "listing watchlists returned $status"
watchlist="$(json "$work/body" "d[0]['id']")"
pass "starter watchlist: $(json "$work/body" "d[0]['name']")"

status="$(request POST "/watchlists/$watchlist/items" "{\"symbol\":\"$SYMBOL\"}")"
[[ "$status" == 201 ]] || die "adding $SYMBOL returned $status: $(cat "$work/body")"
pass "added $SYMBOL"

deadline=$((SECONDS + TIMEOUT_SECONDS))
while :; do
  status="$(request GET "/stocks/$SYMBOL/prices?period=5D")"
  [[ "$status" == 200 ]] || die "price history returned $status"
  state="$(json "$work/body" "d['dataStatus']")"
  [[ "$state" == READY ]] && break
  [[ "$state" == PENDING || "$state" == NOT_TRACKED ]] || die "market data for $SYMBOL is $state"
  (( SECONDS < deadline )) || die "market data for $SYMBOL not ready after ${TIMEOUT_SECONDS}s"
  sleep 1
done
pass "market data collected (provider: $(json "$work/body" "d['dataProvider']"))"

status="$(request GET "/dashboard?period=1M")"
[[ "$status" == 200 ]] || die "dashboard returned $status"
[[ "$(json "$work/body" "len(d['stocks'])")" -ge 1 ]] || die "dashboard has no stocks"
pass "dashboard: $(json "$work/body" "', '.join(s['symbol'] + ' ' + str(s['price']) for s in d['stocks'])")"

status="$(request GET "/stocks/$SYMBOL/analytics")"
[[ "$status" == 200 ]] || die "analytics returned $status"
pass "analytics: return1Y=$(json "$work/body" "d['return1Y']") volatility30d=$(json "$work/body" "d['volatility30d']")"

TOKEN=""
status="$(request POST /auth/refresh)"
[[ "$status" == 200 ]] || die "refresh with cookie returned $status"
TOKEN="$(json "$work/body" "d['accessToken']")"
pass "session refreshed via cookie (token rotated)"

status="$(request POST /auth/logout)"
[[ "$status" == 204 ]] || die "logout returned $status"
TOKEN=""
status="$(request POST /auth/refresh)"
[[ "$status" == 401 ]] || die "refresh after logout returned $status (expected 401)"
pass "logout revoked the session"

echo "Smoke test passed."
