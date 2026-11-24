#!/usr/bin/env bash
# Scénario de validation du rail-bridge. Prérequis : le kit et le bridge tournent (BASE par défaut : localhost:8082).
set -u
BASE=${BASE:-http://localhost:8082/v1}
BODY=/tmp/rail-bridge-last.json

req() {  # req <méthode> <url> [json] [en-têtes...]
  local method=$1 url=$2 data=${3:-} ; shift 3 2>/dev/null || shift $#
  local args=(-s -o "$BODY" -w '%{http_code}' -X "$method" "$url")
  [ -n "$data" ] && args+=(-H 'Content-Type: application/json' -d "$data")
  for h in "$@"; do args+=(-H "$h"); done
  CODE=$(curl "${args[@]}")
  echo "  -> HTTP $CODE  $(cat "$BODY")"
}
field() { sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p" "$BODY" | head -1; }

echo "== search =="
req GET "$BASE/offers?origin=Casablanca&destination=Rabat&date=2026-11-15"
OFFER=$(field offerId)

echo "== hold (1 place) =="
req POST "$BASE/holds" "{\"offerId\":\"$OFFER\",\"seats\":1}"
HOLD=$(field holdId)

echo "== confirm =="
req POST "$BASE/bookings" "{\"holdId\":\"$HOLD\"}"
BOOKING=$(field bookingId)

echo "== cancel (booking), puis rejoué : 204 attendu les deux fois =="
req POST "$BASE/cancellations" "{\"reference\":\"$BOOKING\"}"
req POST "$BASE/cancellations" "{\"reference\":\"$BOOKING\"}"

echo "== échec : trop de places (409 NOT_AVAILABLE attendu) =="
req POST "$BASE/holds" "{\"offerId\":\"$OFFER\",\"seats\":1000}"

echo "== échec : confirm d'un hold inconnu (409 attendu) =="
req POST "$BASE/bookings" '{"holdId":"RAIL:inconnu"}'

echo "== idempotence : même Idempotency-Key, même holdId attendu =="
req POST "$BASE/holds" "{\"offerId\":\"$OFFER\",\"seats\":1}" 'Idempotency-Key: demo-1'
A=$(field holdId)
req POST "$BASE/holds" "{\"offerId\":\"$OFFER\",\"seats\":1}" 'Idempotency-Key: demo-1'
B=$(field holdId)
[ "$A" = "$B" ] && echo "  OK : $A" || echo "  KO : $A != $B"
req POST "$BASE/cancellations" "{\"reference\":\"$A\"}"
