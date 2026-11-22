# ADR-003 — Idempotence des réservations (hold, confirm)

**Statut** : accepté · **Date** : 2026-11-22

## Contexte
La saga rejoue ses appels après un délai dépassé. Le partenaire aérien ne gère aucune clé d'idempotence.
Constats :
- **`confirm` rejoué** : le partenaire répond par un Fault (409 côté bridge) alors que la réservation
  existe. Observé sur un hold déjà confirmé puis annulé : refaire hold, confirm,
  confirm *sans* cancel entre les deux, pour isoler le rejeu pur.
- **`hold` rejoué** : chaque appel renvoie un nouveau `holdId` (`HOLD-AIR-<uuid>`), donc un rejeu crée
  un second hold sans erreur visible: deux `hold` identiques sans clé, comparer les `holdId`.
- Conséquences : doublon silencieux sur `hold`, faux échec sur `confirm` qui déclencherait une
  compensation à tort.

## Décision
Le bridge fournit l'idempotence pour `hold` et `confirm`, via l'en-tête `Idempotency-Key`
(obligatoire, 400 si absent), mémorisée dans **Redis**.

**Protocole**
1. Clé Redis `hold:<K>` ou `confirm:<K>` ; empreinte de la requête (`offerId:seats` pour hold,
   `holdId` pour confirm).
2. `tryLock` : `SET NX` avec TTL 30 s, valeur `{fingerprint, IN_PROGRESS}`. Atomique : une seule
   requête gagne.
3. Premier arrivé : appel du partenaire. Succès → `saveResult` (`DONE` + résultat JSON, TTL 24 h).
   Échec (409/503) → `release`, pour que la saga puisse rejouer.
4. Clé existante → lecture :
    - `DONE` et même empreinte → résultat mémorisé renvoyé, **sans rappeler le partenaire** ;
    - empreinte différente → 422 `IDEMPOTENCY_KEY_REUSED` ;
    - `IN_PROGRESS` → 409 `REQUEST_IN_PROGRESS` ;
    - clé expirée entre-temps → un seul nouvel essai de `tryLock`.
5. `cancel` n'a pas de clé : idempotent par nature (`NOT_FOUND` = succès, 204).
6. Redis indisponible → **503** (échec sûr), pas d'exécution sans protection.

Seuls les succès sont mémorisés : un 503 doit pouvoir être rejoué pour de vrai. Les refus (409) ne le
sont pas non plus, car ils peuvent changer (places libérées).

## Alternatives écartées
| Option | Raison du rejet |
|---|---|
| Mémoire du bridge (`ConcurrentHashMap`) | perdue au redémarrage, cassée dès qu'il y a plusieurs instances |
| Base relationnelle | plus de code (entité, purge planifiée, contrainte unique) et dépendance JPA/JDBC, alors que le bridge n'a pas d'état métier |
| Exécuter sans protection si Redis tombe | un doublon silencieux est pire qu'un refus temporaire |

Redis apporte l'atomicité (`SET NX`), l'expiration native et un état partagé entre instances.

## Conséquences
- Un rejeu renvoie le même résultat, avec un seul appel au partenaire (vérifié : même `holdId` sur deux appels avec la clé `abc123`).
- Redis devient une dépendance d'exécution (service `redis` dans `docker-compose`, AOF, mot de passe, volume).
- Le contrat de la saga impose de générer une clé unique par opération logique et de la réutiliser au rejeu.
- L'accès est isolé derrière l'interface `IdempotencyStore` : Redis remplaçable sans toucher à `AirService`.

## Limites
- **Pas de transaction commune** entre l'appel SOAP et Redis : si le bridge plante après l'appel
  mais avant `saveResult`, un rejeu peut créer un doublon (`hold`) ou un faux 409 (`confirm`).
  Si Redis tombe uniquement à `saveResult`, le résultat est journalisé (`WARN`) et renvoyé, mais non mémorisé.
- Un timeout de lecture laisse un doute sur l'état chez le partenaire ; l'idempotence le rend sûr
  seulement si la saga rejoue avec la même clé.
- TTL de 24 h : au-delà, un rejeu est traité comme une nouvelle requête.