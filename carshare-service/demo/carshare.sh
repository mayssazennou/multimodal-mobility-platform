#!/usr/bin/env bash
# Scénario de validation du carshare-service (même cycle que demo/rail-bridge.sh).
# Prérequis : le service tourne (BASE par défaut : localhost:8084).
set -u
BASE=${BASE:-http://localhost:8084/v1}
BODY=/tmp/carshare-last.json

req() {  # req <méthode> <url> [json] [en-têtes...]
  local method=$1 url=$2 data=${3:-} ; shift 3 2>/dev/null || shift $#
  local args=(-s -o "$BODY" -w '%{http_code}' -X "$method" "$url")
  [ -n "$data" ] && args+=(-H 'Content-Type: application/json' -d "$data")
  for h in "$@"; do args+=(-H "$h"); done
  CODE=$(curl "${args[@]}")
  echo "  -> HTTP $CODE  $(cat "$BODY")"
}
field() { sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p" "$BODY" | head -1; }
seats() { curl -s "$BASE/offers?origin=Paris&destination=Paris&date=2026-11-15" | sed -n 's/.*"seatsAvailable":\([0-9]*\).*/\1/p'; }

echo "== search =="
req GET "$BASE/offers?origin=Paris&destination=Paris&date=2026-11-15"
OFFER=$(field offerId); S0=$(seats)

echo "== hold (1 véhicule) =="
req POST "$BASE/holds" "{\"offerId\":\"$OFFER\",\"seats\":1}"
HOLD=$(field holdId)

echo "== confirm =="
req POST "$BASE/bookings" "{\"holdId\":\"$HOLD\"}"
BOOKING=$(field bookingId)

echo "== cancel (booking), puis rejoué : 204 attendu les deux fois =="
req POST "$BASE/cancellations" "{\"reference\":\"$BOOKING\"}"
req POST "$BASE/cancellations" "{\"reference\":\"$BOOKING\"}"
[ "$(seats)" = "$S0" ] && echo "  OK : stock restitué ($S0)" || echo "  KO : stock $(seats) != $S0"

echo "== échec : trop de véhicules (409 NOT_AVAILABLE attendu) =="
req POST "$BASE/holds" "{\"offerId\":\"$OFFER\",\"seats\":1000}"

echo "== échec : confirm d'un hold inconnu (409 attendu) =="
req POST "$BASE/bookings" '{"holdId":"CAR:inconnu"}'

echo "== échec : identifiant d'un autre opérateur (400 attendu) =="
req POST "$BASE/holds" '{"offerId":"RAIL:RAIL-CAS-RAB-0900","seats":1}'

echo "== idempotence : même Idempotency-Key, même holdId attendu =="
req POST "$BASE/holds" "{\"offerId\":\"$OFFER\",\"seats\":1}" 'Idempotency-Key: demo-car-1'
A=$(field holdId)
req POST "$BASE/holds" "{\"offerId\":\"$OFFER\",\"seats\":1}" 'Idempotency-Key: demo-car-1'
B=$(field holdId)
[ "$A" = "$B" ] && echo "  OK : $A" || echo "  KO : $A != $B"
req POST "$BASE/cancellations" "{\"reference\":\"$A\"}"
