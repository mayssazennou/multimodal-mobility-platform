#!/usr/bin/env bash
# =====================================================================
#  Vérification rapide du starter kit (pour l'enseignant).
#  Ne fait AUCUN appel métier : vérifie seulement que les deux systèmes
#  partenaires sont démarrés et exposent bien leurs contrats.
#  (Écrire le premier client CORBA/SOAP reste le travail des étudiants.)
#
#  Usage :  ./verify.sh      (après "docker compose up --build")
# =====================================================================
set -u
ok=0; ko=0
check() { if eval "$2" >/dev/null 2>&1; then echo "  [OK]  $1"; ok=$((ok+1)); else echo "  [KO]  $1"; ko=$((ko+1)); fi; }

echo "== Partenaire aérien (SOAP) =="
check "WSDL accessible sur http://localhost:8081/ws/air.wsdl" \
      "curl -fsS http://localhost:8081/ws/air.wsdl | grep -q wsdl:definitions"

echo "== Opérateur ferroviaire (CORBA) =="
check "Fichier IOR présent et non vide (ior/RailBooking.ior)" \
      "test -s ior/RailBooking.ior"
check "IOR au format IOR: (corbaloc)" \
      "head -c 4 ior/RailBooking.ior | grep -q IOR"
check "Port IIOP 1050 à l'écoute" \
      "bash -c '</dev/tcp/localhost/1050' 2>/dev/null"

echo
echo "Résultat : $ok OK, $ko KO"
[ "$ko" -eq 0 ] && echo "Le starter kit tourne. ✅" || echo "Voir docs/01-demarrage.md (section Dépannage)."
exit "$ko"
