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

SEARCH="<air:searchFlightsRequest><air:origin>Casablanca</air:origin><air:destination>Paris</air:destination><air:date>2026-11-15</air:date></air:searchFlightsRequest>"

echo "== search =="
soap "$SEARCH"

echo "== hold =="
FLIGHT_ID=AIR-CMN-ORY-1330
SEATS=2
soap "<air:holdFlightRequest><air:flightId>$FLIGHT_ID</air:flightId><air:seats>$SEATS</air:seats></air:holdFlightRequest>"
HOLD_ID=$(val holdId)

echo "== search (58 attendu) =="
soap "$SEARCH"

echo "== confirm =="
soap "<air:confirmFlightRequest><air:holdId>$HOLD_ID</air:holdId></air:confirmFlightRequest>"
REF=$(val bookingRef)

echo "== cancel =="
soap "<air:cancelFlightRequest><air:reference>$REF</air:reference></air:cancelFlightRequest>"

echo "== search (60 attendu) =="
soap "$SEARCH"

echo "== cancel rejoué x2 (test d'idempotence) =="
soap "<air:cancelFlightRequest><air:reference>$REF</air:reference></air:cancelFlightRequest>"
soap "<air:cancelFlightRequest><air:reference>$REF</air:reference></air:cancelFlightRequest>"
