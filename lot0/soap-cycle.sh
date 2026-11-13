#!/usr/bin/env bash
set -u
command -v xmllint >/dev/null || { echo "xmllint manquant : sudo apt install libxml2-utils"; exit 1; }

URL=http://localhost:8081/ws
BODY=/tmp/soap-last.xml

soap() {
  local code
  code=$(curl -sS "$URL" \
    -H "Content-Type: text/xml; charset=utf-8" \
    -d "<soapenv:Envelope xmlns:soapenv='http://schemas.xmlsoap.org/soap/envelope/' xmlns:air='http://mobility.ma/air'><soapenv:Body>$1</soapenv:Body></soapenv:Envelope>" \
    -o "$BODY" -w '%{http_code}')
  echo "HTTP $code"
  xmllint --format "$BODY"
  echo
}

# extrait le texte d'une balise de la dernière réponse
val() { xmllint --xpath "string(//*[local-name()='$1'])" "$BODY"; }

hold()    { soap "<air:holdFlightRequest><air:flightId>$1</air:flightId><air:seats>$2</air:seats></air:holdFlightRequest>"; }
confirm() { soap "<air:confirmFlightRequest><air:holdId>$1</air:holdId></air:confirmFlightRequest>"; }
cancel()  { soap "<air:cancelFlightRequest><air:reference>$1</air:reference></air:cancelFlightRequest>"; }

echo "==== CAS NOMINAL ===="

echo "== search =="
SEARCH="<air:searchFlightsRequest><air:origin>Casablanca</air:origin><air:destination>Paris</air:destination><air:date>2026-11-15</air:date></air:searchFlightsRequest>"
soap "$SEARCH"

echo "== hold =="
hold AIR-CMN-ORY-1330 2
HOLD_ID=$(val holdId)

echo "== search (58 attendu) =="
soap "$SEARCH"

echo "== confirm =="
confirm "$HOLD_ID"
REF=$(val bookingRef)

echo "== cancel =="
cancel "$REF"

echo "== search (60 attendu) =="
soap "$SEARCH"

echo "== cancel rejoué x2 (test d'idempotence) =="
cancel "$REF"
cancel "$REF"


echo "==== CAS D'ECHEC ===="

echo "== hold (places insuffisantes) ==" 
hold AIR-CMN-MAD-1800 4

echo "== confirm avec un holdId inventé =="
confirm xxx
