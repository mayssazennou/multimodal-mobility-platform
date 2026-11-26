# ADR-006 : Conception du service d'autopartage (carshare-service)

- **Statut** : Accepté
- **Date** : 2026-11-24
- **Contexte du lot** : Lot 1-2 (microservice interne, participant à la saga)

## Contexte

Le scénario central est une réservation multimodale train + avion + autopartage,
orchestrée par une saga. Le rail (CORBA) et l'aérien (SOAP) sont derrière des
bridges REST qui exposent le cycle search → hold → confirm / cancel.
L'autopartage est un service natif de la plateforme : il n'a aucune contrainte
de protocole héritée et peut donc être conçu librement. Il doit pourtant
s'insérer dans la saga sans cas particulier.

## Décision 1 : contrat REST aligné sur l'air-bridge

Le service expose les mêmes opérations et la même forme que l'air-bridge :

| Opération | Endpoint |
|---|---|
| search | `GET /v1/offers?city=&from=&to=` |
| hold | `POST /v1/holds` (`Idempotency-Key`) |
| confirm | `POST /v1/holds/{holdId}/confirm` |
| cancel | `POST /v1/cancellations` (`{reference}`) |

Les paramètres de recherche et le corps du hold dépendent de la ressource
(créneau et ville ici, trajet et places ailleurs). La forme des réponses
(`Hold`, `Booking`) reste identique.

**Alternatives écartées** : un contrat propre à l'autopartage (REST « idiomatique »,
ex. `DELETE /bookings/{ref}`) ; plus élégant, mais il force l'orchestrateur à
avoir un chemin de code par opérateur.

**Conséquence** : l'orchestrateur traite les trois opérateurs de manière
homogène ; seul l'adaptateur vers le modèle canonique change. Le champ `seats`
est accepté mais ignoré (une voiture = une réservation).

## Décision 2 : base PostgreSQL dédiée, anti-chevauchement garanti en base

Le service possède sa propre base (`carshare-db`), non partagée. L'absence de
double réservation est garantie par une contrainte d'exclusion PostgreSQL
(`EXCLUDE USING gist` sur `vehicle_id` et `tstzrange(start_at, end_at)`,
limitée aux statuts `HELD` et `CONFIRMED`).

**Alternatives écartées** :
- Vérification applicative (lire puis écrire) : sujette aux conditions de
  course sous concurrence.
- Verrou pessimiste sur la ligne `vehicle` : correct, mais sérialise toutes
  les réservations d'un même véhicule et dépend du code applicatif.
- H2 : ne supporte pas la contrainte d'exclusion.

## Décision 3 : schéma géré par Flyway

Le schéma et les données initiales sont dans `V1__init.sql`, exécuté au
démarrage du service. Hibernate est en `ddl-auto=validate`.

**Raison** : la contrainte d'exclusion n'est pas générable par Hibernate, et
`docker compose up` doit suffire à obtenir une base prête.

## Décision 4 : idempotence par clé, par statut et par tolérance

| Opération | Mécanisme |
|---|---|
| hold | `Idempotency-Key` unique en base ; clé rejouée = même `Hold` |
| confirm | déjà `CONFIRMED` = même `Booking` renvoyé |
| cancel | référence inconnue ou déjà annulée = 204 |

**Raison** : les appels de la saga peuvent être rejoués (timeouts, reprises
après échec), et l'annulation compensatoire doit être rejouable sans erreur.

**Limite connue** : deux requêtes simultanées avec la même clé peuvent faire
échouer la seconde en 409 au lieu de renvoyer le même hold. Aucun risque de
double réservation, car la contrainte de la décision 2 s'applique.

## Décision 5 : holds temporaires avec expiration

Un hold a une durée de vie (`carshare.hold-ttl-minutes`, 10 min). Un job
`@Scheduled` (toutes les 30 s) passe les holds périmés à `EXPIRED`, ce qui
libère le créneau. `confirm` refuse un hold expiré.

**Raison** : si la saga plante après le hold, la voiture ne doit pas rester
bloquée indéfiniment.

**Conséquence** : un hold périmé bloque le créneau jusqu'à 30 s de plus que son
TTL (délai du job), ce qui est accepté.

## Erreurs

Une indisponibilité (`NotAvailable`) est renvoyée en **409** avec un corps
`application/problem+json`, afin que l'orchestrateur ait un seul chemin
d'erreur pour les trois opérateurs. Les entrées invalides donnent un 400.

## Limites et évolutions prévues

- **Pas de release explicite** d'un hold : en cas d'échec avant `confirm`, le
  créneau est libéré par expiration. Un `DELETE /v1/holds/{holdId}` pourra
  être ajouté sur les trois bridges.
