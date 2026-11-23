# Contrat REST commun des opérateurs — v1

**Source de référence en Java** : le projet Maven `canonical-model` (package `ma.mobility.canonical`, Java 25).

**Vérification** : `./demo/conformite.sh [rail|air|car|all]` rejoue le même scénario contre chaque opérateur.

Ce document décrit ce que **rail, air et autopartage exposent en REST** et que la saga et la gateway appellent
de la même façon. Aucun client ne voit jamais de CORBA ou de SOAP : chaque bridge traduit vers ce contrat
(couche anti-corruption). Un changement de ce contrat passe par une pull request relue par l'autre membre de l'équipe.

## 1. Opérateurs concernés

| Service | Mode | Technologie en interne | Port hôte | URL dans le réseau Docker |
|---|---|---|---|---|
| `rail-bridge` | `RAIL` | CORBA / JacORB (Java 8) | 8082 | `http://rail-bridge:8080/v1` |
| `air-bridge` | `AIR` | SOAP / Spring WS | 8083 *(proposé)* | `http://air-bridge:8080/v1` |
| `carshare-service` | `CAR` | REST natif, base propre | 8084 *(proposé)* | `http://carshare-service:8080/v1` |

## 2. Endpoints

| Méthode et chemin | Corps | Succès | Rôle |
|---|---|---|---|
| `GET /v1/offers?origin=&destination=&date=AAAA-MM-JJ` | — | `200` + `Offer[]` | Recherche |
| `POST /v1/holds` | `HoldRequest` | `201` + `Hold` | Maintien provisoire (phase 1 de la saga) |
| `POST /v1/bookings` | `BookingRequest` | `201` + `Booking` | Confirmation d'un maintien (phase 2) |
| `POST /v1/cancellations` | `CancelRequest` | `204` | Annulation / compensation d'un maintien **ou** d'une réservation |

`POST /v1/holds` et `POST /v1/bookings` acceptent l'en-tête optionnel `Idempotency-Key`. Pour la propagation de
la trace, les services lisent et transmettent l'en-tête `traceparent` (OpenTelemetry).

## 3. Objets et correspondance avec `canonical-model`

| JSON | Classe Java (`ma.mobility.canonical`) | Utilisé par |
|---|---|---|
| `Offer`, avec `price` de type `Money` | `Offer`, `Money`, `Mode` | réponse de `GET /offers` |
| `HoldRequest` | `HoldRequest` | corps de `POST /holds` |
| `Hold` | `Hold`, `HoldStatus` | réponse de `POST /holds` |
| `BookingRequest` | `BookingRequest` | corps de `POST /bookings` |
| `Booking` | `Booking`, `BookingStatus` | réponse de `POST /bookings` |
| `CancelRequest` | `CancelRequest` | corps de `POST /cancellations` |
| `ApiError` | `ApiError`, `ErrorCode` | corps de toute réponse d'erreur |

Les enums sont sérialisées par leur nom (`"RAIL"`, `"HELD"`, `"CONFIRMED"`…). Avec Jackson, il faut
`spring.jackson.serialization.write-dates-as-timestamps=false` pour obtenir des dates ISO-8601.
`rail-bridge` est en Java 8 et ne peut pas dépendre de ce module : il réimplémente les mêmes champs
(`api/Dto.java`) et respecte le même JSON.

### Offer — réponse de `GET /v1/offers` (liste)
```json
{
  "offerId": "RAIL:RAIL-CAS-RAB-0730",
  "mode": "RAIL",
  "providerRef": "RAIL-CAS-RAB-0730",
  "origin": "Casablanca",
  "destination": "Rabat",
  "departure": "2026-11-15T07:30:00",
  "arrival": "2026-11-15T08:25:00",
  "price": { "amountMinor": 8000, "currency": "MAD" },
  "seatsAvailable": 120
}
```
Exemple aérien : `"offerId": "AIR:AIR-CMN-ORY-1330"`, `"price": { "amountMinor": 160000, "currency": "MAD" }`, `"seatsAvailable": 60`.

### HoldRequest — corps de `POST /v1/holds`
```json
{ "offerId": "RAIL:RAIL-CAS-RAB-0900", "seats": 1 }
```

### Hold — réponse de `POST /v1/holds`
```json
{
  "holdId": "RAIL:HOLD-RAIL-90969a23-170f-4773-a64f-945feb6c6e5b",
  "mode": "RAIL",
  "providerHoldId": "HOLD-RAIL-90969a23-170f-4773-a64f-945feb6c6e5b",
  "offerId": "RAIL:RAIL-CAS-RAB-0900",
  "quantity": 1,
  "status": "HELD",
  "createdAt": "2026-11-22T22:58:41.393Z",
  "expiresAt": null
}
```

### BookingRequest — corps de `POST /v1/bookings`
```json
{ "holdId": "RAIL:HOLD-RAIL-90969a23-170f-4773-a64f-945feb6c6e5b" }
```

### Booking — réponse de `POST /v1/bookings`
```json
{
  "bookingId": "RAIL:BK-RAIL-436893df-0cdc-43d5-8ef3-1aab90583aaf",
  "mode": "RAIL",
  "providerBookingRef": "BK-RAIL-436893df-0cdc-43d5-8ef3-1aab90583aaf",
  "holdId": "RAIL:HOLD-RAIL-90969a23-170f-4773-a64f-945feb6c6e5b",
  "status": "CONFIRMED"
}
```

### CancelRequest — corps de `POST /v1/cancellations`
```json
{ "reference": "RAIL:BK-RAIL-436893df-0cdc-43d5-8ef3-1aab90583aaf" }
```
`reference` contient un `holdId` ou un `bookingId` (voir §5.3).

### ApiError — corps de toute erreur
```json
{
  "code": "NOT_AVAILABLE",
  "message": "Plus assez de places sur RAIL-CAS-RAB-0900 (demandé 1000, disponible 120)",
  "correlationId": "93d12325-7e1f-4a6a-a593-23dcd88297ae"
}
```

Les exemples `Offer` rail, `Hold`, `Booking` et `ApiError` proviennent de la sortie réelle de `rail-bridge`.

## 4. Erreurs

| HTTP | `code` | Signification | Compté par le circuit breaker ? |
|---|---|---|---|
| `400` | `INVALID_REQUEST` | Requête invalide (ex. `seats` < 1, identifiant d'un autre opérateur) | non |
| `409` | `NOT_AVAILABLE` | **L'opérateur a répondu non** : plus de places, maintien inconnu ou expiré | **non** (refus métier) |
| `503` | `PROVIDER_UNAVAILABLE` | **Impossible de parler à l'opérateur** : connexion perdue, délai dépassé | **oui** |

La distinction 409 / 503 est essentielle : côté SOAP un refus métier arrive en HTTP 500 avec un Fault générique,
et côté CORBA en exception IDL typée. Chaque bridge les ramène au même `409`, et aucun client ne parse jamais le
texte d'un message. Sans cette règle, quelques refus de places consécutifs ouvriraient le circuit breaker alors
que l'opérateur fonctionne.

## 5. Règles de comportement (ce que les classes ne montrent pas)

1. **Identifiants préfixés par le mode** : `RAIL:…`, `AIR:…`, `CAR:…`. Un service accepte aussi l'identifiant brut en entrée, et rejette (`400`) un identifiant portant le préfixe d'un autre opérateur.
2. **`hold` décrémente provisoirement `seatsAvailable`** ; une annulation les restitue.
3. **Annulation** : `POST /v1/cancellations` reçoit `{ "reference": "…" }`. La référence accepte un `holdId` (compensation avant confirmation) **ou** un `bookingId`; le endpoint répond **toujours `204`**, même pour une référence déjà annulée ou inconnue. Seule exception : opérateur injoignable (`503`), la saga doit alors rejouer.
4. **Idempotence** : avec une même `Idempotency-Key`, `POST /holds` et `POST /bookings` renvoient le résultat de la première exécution sans solliciter l'opérateur à nouveau (pas de second maintien). `rail-bridge` la garde en mémoire (perdue au redémarrage).
5. **Montants** : unité mineure (centimes), toujours `long`, avec `currency` (`MAD`).
6. **Dates** : ISO-8601 **sans fuseau** (`2026-11-15T07:30:00`), comme les contrats legacy ; `createdAt` est un instant UTC (`…Z`).
7. **Expiration** : les opérateurs legacy ne renvoient pas d'échéance, donc `expiresAt` vaut `null`. Les maintiens expirent côté opérateur au bout de 10 minutes : la saga doit confirmer rapidement.
8. **`seats` ≥ 1** ; une valeur inférieure répond `400 INVALID_REQUEST`.
9. **Autopartage (`CAR`)** : (à confirmer) `origin` = lieu de prise en charge, `destination` = lieu de restitution (peuvent être égaux), `seatsAvailable` et `seats` comptent des véhicules.

## 6. Statuts

- **`HoldStatus`** (un maintien) : `HELD`, `CONFIRMED`, `CANCELLED`.
- **`BookingStatus`** (une réservation chez **un** opérateur) : `CONFIRMED`, `CANCELLED`.
- **`TravelStatus`** (le dossier de voyage **complet**, voir ci-dessous) : `PENDING`, `CONFIRMED`, `CANCELLED`, `FAILED`.

`BookingStatus` décrit une réservation unique ; `TravelStatus` décrit l'ensemble train + avion + autopartage.
Les deux évoluent indépendamment : un dossier est `PENDING` tant que les trois réservations ne sont pas toutes
confirmées, `CANCELLED` quand la saga a compensé, `FAILED` si une compensation elle-même échoue (une réservation
peut alors rester `CONFIRMED`, c'est un état incohérent à signaler).

## 7. Hors périmètre de ce document

- Le **dossier de voyage** (`TravelFile`, `TravelStatus`) est géré par le `booking-service` et vu par la gateway.
  Ses routes (`POST /bookings`, `GET /bookings/{id}`, mise à jour du statut) sont **distinctes** des routes
  d'opérateur ci-dessus malgré le nom : `/v1/bookings` d'un opérateur confirme un maintien, celle du
  `booking-service` crée un dossier. Son contrat est à documenter.
- `OperatorPort` (module `canonical-model`) est l'interface Java que la saga peut implémenter avec un client HTTP par
  opérateur (utile pour ses mocks). Elle n'est pas un contrat réseau.
- Le format des événements Kafka (`HoldCreated`, `BookingConfirmed`, `SagaFailed`, `BookingCancelled`).

## 8. À confirmer

1. `POST /cancellations` reçoit une référence (`holdId` ou `bookingId`) et répond toujours `204` (§5.3).
2. `expiresAt` toujours `null` pour le legacy (§5.7).
3. Identifiants préfixés par le mode, acceptés aussi bruts en entrée (§5.1).
4. Codes d'erreur et statuts HTTP du §4, en particulier `409` pour tout refus, y compris le Fault SOAP.
5. `air-bridge` et `carshare-service` passent `demo/conformite.sh` avant toute fusion dans `main`.
