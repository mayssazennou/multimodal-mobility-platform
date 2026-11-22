# ADR-004 — Résilience du bridge aérien (timeouts, circuit breaker)

**Statut** : accepté · **Date** : 2026-11-22

## Contexte
Le partenaire aérien est un système externe que nous ne contrôlons pas. S'il devient lent
ou muet, le bridge attend sa réponse avec un thread de Tomcat. Sans protection :
- aucune exception n'est levée (l'appel traîne), donc ni le 503 ni la saga ne réagissent ;
- les threads du bridge s'épuisent, et le bridge devient indisponible à son tour
  (panne en cascade) ;
- le partenaire déjà en difficulté continue de recevoir des requêtes.

L'énoncé (Lot 3) demande des délais d'attente, des circuit breakers et des reprises.

## Décision

### 1. Timeouts sur le client SOAP
- connexion : **2 s** ; lecture : **3 s** (`HttpUrlConnectionMessageSender`).
- un dépassement lève une `WebServiceIOException`, traduite en `OperatorUnavailableException`
  (HTTP 503 `PROVIDER_UNAVAILABLE`).

### 2. Circuit breaker (Resilience4j 2.4.0)
- un breaker unique `air-partner`, appliqué dans `AirService.callPartner`, donc à `search`,
  `hold`, `confirm` et `cancel`.
- **mode programmatique** (`CircuitBreaker.executeSupplier`) : pas d'AOP, pas de dépendance au
  support Spring Boot de Resilience4j, donc pas de risque d'incompatibilité avec Spring Boot 4.
  La version 2.4.0 n'étant pas gérée par le BOM Spring, elle est déclarée explicitement.
- **le breaker enveloppe l'appel et la traduction des exceptions** : il voit donc les
  exceptions canoniques et non celles de Spring WS.

| Réglage | Valeur | Rôle |
|---|---|---|
| fenêtre | 4 appels (`COUNT_BASED`) **[À ADAPTER]** | historique observé |
| appels minimum | 4 **[À ADAPTER]** | pas de jugement sur un échantillon trop petit |
| seuil d'échecs | 50 % | ouverture du circuit |
| durée d'ouverture | 5 s **[À ADAPTER]** | délai avant l'essai |
| essais en HALF-OPEN | 2 **[À ADAPTER]** | reprise prudente |

Ces valeurs sont celles de la démonstration. Pour la version rendue : 10 / 5 / 10 s / 3.

### 3. Ce qui compte comme échec (ADR-001, règle 4)
| Résultat | Effet sur le breaker |
|---|---|
| `OperatorUnavailableException` (panne, timeout, réponse invalide) | **échec compté** |
| `OperatorRefusedException` (409, refus métier) | **ignoré** : le partenaire fonctionne, il dit non |
| erreurs de validation (400), conflits d'idempotence (422, 409) | jamais vus : elles surviennent avant l'appel |
| résultat rejoué depuis Redis | jamais vu : aucun appel au partenaire |

### 4. Circuit ouvert
`CallNotPermittedException` est traduite en `OperatorUnavailableException` (503, message
« Circuit ouvert »), sans appeler le partenaire. Aucune modification n'est nécessaire côté
`GlobalExceptionHandler`.

### 5. Interaction avec l'idempotence (ADR-003)
Si le circuit est ouvert pendant un `hold` ou un `confirm`, la note `IN_PROGRESS` posée par
`tryLock` est libérée (`release`) : la saga peut rejouer avec la même `Idempotency-Key` sans
être bloquée par sa propre note.

### 6. Reprises (retry)
**Le bridge ne rejoue jamais lui-même un appel au partenaire.** Les reprises sont de la
responsabilité de la saga, avec un nombre de tentatives borné et un backoff exponentiel.
Un 503 est rejouable (même `Idempotency-Key`), un 409 est définitif (compensation).

## Alternatives écartées
| Option | Raison du rejet |
|---|---|
| Timeouts seuls | un partenaire en panne coûte 3 s par requête et occupe un thread à chaque fois |
| Retry dans le bridge | multiplie les appels vers un partenaire déjà en difficulté ; la saga seule voit l'opération complète et détient la clé d'idempotence |
| `@CircuitBreaker` (annotations, starter Spring Boot) | dépend de l'AOP et de la compatibilité avec Spring Boot 4 ; plus opaque à expliquer |
| Un breaker par opération | on mesure la santé du partenaire, pas celle d'une opération |
| Bulkhead (cloison) | utile pour isoler plusieurs partenaires dans un même processus ; non nécessaire ici, un bridge par partenaire |

## Preuves
Test réalisé avec `docker compose pause air` (partenaire figé), fenêtre de 4 appels :
- appels 1 à 3 : 3,14 s, 3,05 s, 3,07 s (timeout de lecture), puis circuit ouvert ;
- appel suivant : **0,048 s**, 503 « Circuit ouvert », partenaire non appelé ;
- après `unpause` et 5 s : `200`, transitions `OPEN → HALF_OPEN → CLOSED` ;
- six `hold` à 999 places, avec des clés distinctes : six `409`, **aucun `503`**,
  circuit resté fermé ;
- un `hold` pendant l'ouverture : 503 instantané, clé Redis libérée, rejeu possible.

Le circuit s'est ouvert au 3e échec et non au 4e, car l'appel de vérification précédant la
mise en pause (un succès) comptait dans la fenêtre de 4 appels.

## Conséquences
- Un partenaire en panne produit un 503 en 0,05 s au lieu de 3 s, les threads du bridge
  restent disponibles et le partenaire n'est plus sollicité.
- La reprise est automatique : le breaker teste le partenaire en HALF-OPEN et se referme seul.
- La saga peut décider vite (réessayer, ou répondre avec le train et la voiture sans l'avion).

## Limites
- **État en mémoire**, propre à chaque instance du bridge : deux conteneurs auraient chacun
  leur compteur. Un état partagé (Redis) est possible mais non retenu pour ce périmètre.
- **Un seul breaker pour les quatre opérations** : une série d'échecs sur `search` ouvre aussi
  le circuit pour `hold` et `confirm`. Choix volontaire (c'est la santé du partenaire que l'on mesure).
- Seuil et fenêtre non calibrés sur du trafic réel : valeurs de départ à ajuster.