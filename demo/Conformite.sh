#!/usr/bin/env bash
# Test de conformité du contrat REST commun (voir docs/contrats.md).
# Le même scénario est rejoué contre chaque opérateur : rail-bridge, air-bridge, carshare-service.
#
# Usage   : ./demo/conformite.sh [rail|air|car|all]      (défaut : all)
# Réglage : variables d'environnement (valeurs par défaut ci-dessous), par exemple
#           AIR_BASE=http://air-bridge:8080/v1 CAR_ORIGIN=Rabat ./demo/conformite.sh car
# Prérequis : la première offre renvoyée par la recherche doit avoir au moins 1 place.
# Code de sortie : 0 si tout est conforme, 1 sinon.
set -u

# --- URL de base de chaque opérateur ---------------------------------------------------------
RAIL_BASE=${RAIL_BASE:-http://localhost:8082/v1}
AIR_BASE=${AIR_BASE:-http://localhost:8083/v1}
CAR_BASE=${CAR_BASE:-http://localhost:8084/v1}

# --- Paramètres de recherche de chaque opérateur ----------------------------------------------
RAIL_ORIGIN=${RAIL_ORIGIN:-Casablanca}; RAIL_DESTINATION=${RAIL_DESTINATION:-Rabat};      RAIL_DATE=${RAIL_DATE:-2026-11-15}
AIR_ORIGIN=${AIR_ORIGIN:-Casablanca};   AIR_DESTINATION=${AIR_DESTINATION:-Paris};        AIR_DATE=${AIR_DATE:-2026-11-15}
CAR_ORIGIN=${CAR_ORIGIN:-Casablanca};   CAR_DESTINATION=${CAR_DESTINATION:-Casablanca};   CAR_DATE=${CAR_DATE:-2026-11-15}

BODY=$(mktemp)
trap 'rm -f "$BODY"' EXIT
PASS=0; FAIL=0; CODE=000; EXTRA=""

# --- Outils ------------------------------------------------------------------------------------
call() {  # call <méthode> <url> [json]   ->  CODE et BODY ; EXTRA = en-tête supplémentaire éventuel
  local args=(-s -o "$BODY" -w '%{http_code}' -X "$1" "$2")
  [ -n "${3:-}" ] && args+=(-H 'Content-Type: application/json' -d "$3")
  [ -n "$EXTRA" ] && args+=(-H "$EXTRA")
  CODE=$(curl "${args[@]}" 2>/dev/null) || CODE=000
}
field() { grep -o "\"$1\":\"[^\"]*\"" "$BODY" | head -1 | cut -d'"' -f4; }   # 1re valeur texte
num()   { grep -o "\"$1\":[0-9]*" "$BODY" | head -1 | cut -d: -f2; }          # 1re valeur numérique
enc()   { echo "${1// /%20}"; }

ok() { PASS=$((PASS + 1)); echo "  [OK] $1"; }
ko() { FAIL=$((FAIL + 1)); echo "  [KO] $1 : $2"; echo "       réponse : $(head -c 300 "$BODY")"; }
expect_code() { [ "$CODE" = "$2" ] && ok "$1 (HTTP $CODE)" || ko "$1" "HTTP $CODE au lieu de $2"; }
expect_eq()   { [ "$2" = "$3" ] && ok "$1" || ko "$1" "obtenu '$2', attendu '$3'"; }

# --- Scénario pour un opérateur -----------------------------------------------------------------
run() {  # run <MODE> <base> <origine> <destination> <date>
  local mode=$1 base=$2 origin=$3 dest=$4 date=$5
  local search="$base/offers?origin=$(enc "$origin")&destination=$(enc "$dest")&date=$date"
  echo
  echo "=== $mode  ($base) ==="

  call GET "$search"
  if [ "$CODE" = "000" ]; then ko "service joignable" "aucune réponse de $base"; return; fi
  expect_code "recherche" 200
  local offer seats0
  offer=$(field offerId); seats0=$(num seatsAvailable)
  if [ -z "$offer" ] || [ -z "$seats0" ]; then ko "au moins une offre" "vérifier les paramètres de recherche ($origin -> $dest, $date)"; return; fi
  case "$offer" in "$mode":*) ok "offerId préfixé par $mode:" ;; *) ko "offerId préfixé par $mode:" "obtenu '$offer'" ;; esac
  [ -n "$(num amountMinor)" ] && ok "prix en unité mineure (amountMinor)" || ko "prix en unité mineure" "amountMinor absent"
  [ "$seats0" -ge 1 ] 2>/dev/null || { ko "première offre avec au moins 1 place" "seatsAvailable=$seats0"; return; }

  echo "  -- cycle nominal"
  call POST "$base/holds" "{\"offerId\":\"$offer\",\"seats\":1}"
  expect_code "hold" 201
  local hold; hold=$(field holdId)
  expect_eq "hold : status HELD" "$(field status)" "HELD"
  expect_eq "hold : mode $mode" "$(field mode)" "$mode"
  call GET "$search"
  expect_eq "le hold décrémente les places" "$(num seatsAvailable)" "$((seats0 - 1))"

  call POST "$base/bookings" "{\"holdId\":\"$hold\"}"
  expect_code "confirm" 201
  local booking; booking=$(field bookingId)
  expect_eq "confirm : status CONFIRMED" "$(field status)" "CONFIRMED"

  call POST "$base/cancellations" "{\"reference\":\"$booking\"}"; expect_code "cancel" 204
  call POST "$base/cancellations" "{\"reference\":\"$booking\"}"; expect_code "cancel rejoué (idempotent)" 204
  call GET "$search"
  expect_eq "le cancel restitue les places" "$(num seatsAvailable)" "$seats0"

  echo "  -- refus et erreurs"
  call POST "$base/holds" "{\"offerId\":\"$offer\",\"seats\":1000}"
  expect_code "hold de trop de places" 409
  expect_eq "  code NOT_AVAILABLE" "$(field code)" "NOT_AVAILABLE"
  call POST "$base/bookings" "{\"holdId\":\"$mode:inconnu\"}"
  expect_code "confirm d'un maintien inconnu" 409
  call POST "$base/holds" "{\"offerId\":\"$offer\",\"seats\":0}"
  expect_code "hold de 0 place" 400
  expect_eq "  code INVALID_REQUEST" "$(field code)" "INVALID_REQUEST"
  call POST "$base/cancellations" "{\"reference\":\"$mode:inconnu\"}"
  expect_code "cancel d'une référence inconnue" 204

  echo "  -- idempotence et annulation d'un maintien"
  EXTRA="Idempotency-Key: conformite-$mode-$$"
  call POST "$base/holds" "{\"offerId\":\"$offer\",\"seats\":1}"; local h1; h1=$(field holdId); expect_code "hold avec Idempotency-Key" 201
  call POST "$base/holds" "{\"offerId\":\"$offer\",\"seats\":1}"; local h2; h2=$(field holdId)
  EXTRA=""
  expect_eq "même clé, même holdId (pas de second maintien)" "$h2" "$h1"
  call POST "$base/cancellations" "{\"reference\":\"$h1\"}"; expect_code "cancel d'un holdId (compensation avant confirm)" 204
  call GET "$search"
  expect_eq "places restituées après annulation du maintien" "$(num seatsAvailable)" "$seats0"
}

# --- Sélection des opérateurs -------------------------------------------------------------------
target=$(echo "${1:-all}" | tr 'A-Z' 'a-z')
case "$target" in rail|air|car|all) ;; *) echo "Usage : $0 [rail|air|car|all]"; exit 2 ;; esac
[ "$target" = all ] || [ "$target" = rail ] && run RAIL "$RAIL_BASE" "$RAIL_ORIGIN" "$RAIL_DESTINATION" "$RAIL_DATE"
[ "$target" = all ] || [ "$target" = air ]  && run AIR  "$AIR_BASE"  "$AIR_ORIGIN"  "$AIR_DESTINATION"  "$AIR_DATE"
[ "$target" = all ] || [ "$target" = car ]  && run CAR  "$CAR_BASE"  "$CAR_ORIGIN"  "$CAR_DESTINATION"  "$CAR_DATE"

echo
echo "Bilan : $PASS vérification(s) réussie(s), $FAIL échec(s)."
[ "$FAIL" -eq 0 ]
