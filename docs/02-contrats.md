# 02 — Contrats des systèmes fournis

Les deux contrats sont **miroir** l'un de l'autre : mêmes quatre opérations,
même cycle de vie. C'est ce qui vous permet d'écrire une saga homogène
malgré deux protocoles différents. Les champs monétaires sont en **centimes de
MAD** (`priceMinor`) ; les dates sont en **ISO-8601** ; le paramètre `date` de
recherche est au format **`AAAA-MM-JJ`**.

## Opérateur ferroviaire — CORBA (IDL)

Fichier : `rail-operator-corba/src/main/idl/rail.idl`

| Opération | Signature | Renvoie / lève |
|---|---|---|
| search  | `search(origin, destination, date)` | `TripList` (liste, possiblement vide) |
| hold    | `hold(tripId, seats)` | `holdId` · lève `NotAvailable` |
| confirm | `confirm(holdId)` | `bookingRef` · lève `NotAvailable` |
| cancel  | `cancel(bookingRef)` | *(void)* — idempotent, accepte holdId **ou** bookingRef |

Bootstrap : l'objet est publié via **IOR fichier** (`ior/RailBooking.ior`).
`orb.string_to_object(ior)` puis `RailBookingHelper.narrow(...)`.

## Partenaire aérien — SOAP (XSD → WSDL)

Schéma : `air-partner-soap/src/main/resources/xsd/air.xsd`
WSDL (runtime) : `http://localhost:8081/ws/air.wsdl`
Namespace : `http://mobility.ma/air`

| Message | Entrée | Sortie / fault |
|---|---|---|
| searchFlights  | origin, destination, date | `flight*` |
| holdFlight     | flightId, seats | `holdId` · fault si indisponible |
| confirmFlight  | holdId | `bookingRef` · fault si indisponible |
| cancelFlight   | reference (holdId ou bookingRef) | `status` — idempotent |

## Jeu de données (figé)

**Trajets ferroviaires** (extraits) : Casablanca→Rabat (07:30, 09:00),
Casablanca→Marrakech, Rabat→Tanger, Casablanca→Fès, Rabat→Casablanca — le
2026-11-15 (plus une occurrence le 2026-11-16).

**Vols aériens** (extraits) : Rabat→Paris, Casablanca→Paris, Casablanca→Dakar,
Marrakech→Casablanca, Casablanca→Madrid, Tanger→Casablanca — le 2026-11-15.

> `AIR-CMN-MAD-1800` n'a que **3 places** : utilisez-le pour déclencher un
> `hold` qui échoue et exercer la compensation de votre saga.

Un trajet multimodal plausible pour la démonstration : **train**
Casablanca→Rabat, puis **vol** Rabat→Paris, plus un **autopartage** à l'arrivée
(votre microservice interne).
