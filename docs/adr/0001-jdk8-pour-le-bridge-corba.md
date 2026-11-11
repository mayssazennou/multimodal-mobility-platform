# ADR-0001 — JDK 8 pour le module bridge-rail (client CORBA)

- **Statut** : Acceptée
- **Date** : 2026-11-10

## Contexte

- L'énoncé demande Java 17 ou supérieur pour la plateforme (Spring Boot).
- Le bridge ferroviaire doit appeler l'opérateur CORBA avec JacORB 3.9, à partir de l'IDL publié.
- Dans JacORB 3.9, `org.jacorb.orb.Reference` étend `javax.rmi.CORBA.Stub`. Ce paquet a quitté le JDK avec Java 11 (suppression des modules CORBA et Java EE).
- Constaté dans `lot0/corba-client` : sous JDK 17, la génération des stubs, la compilation et `ORB.init` réussissent, mais `string_to_object` échoue avec `NoClassDefFoundError: org.jacorb.orb.Reference`.
- Le serveur ferroviaire du kit est lui-même compilé en Java 8, pour cette raison.

## Options considérées

1. **Tout en JDK 17+, sans traitement** — écartée : échec constaté.
2. **JDK 17+ avec `javax.rmi.CORBA` fourni** (dépendance tierce, ou classe `Stub` écrite à la main) — écartée pour l'instant : la dépendance n'est pas identifiée, la classe manuelle est un bricolage dans le paquet `javax.*`, et d'autres classes de JacORB dépendent de `javax.rmi` (flux CDR, `ValueHandler`) : risque non évalué.
3. **Module bridge-rail isolé en JDK 8** — retenue.

## Décision

Le module `bridge-rail` (CORBA ↔ REST) est construit et exécuté en Java 8 (Spring Boot 2.7.x), dans son propre conteneur. Le reste de la plateforme reste en Java 17 ou plus.

## Conséquences

- (+) Compatibilité avec JacORB, éprouvée par le serveur du kit.
- (+) La version du JDK reste un détail interne du bridge : les autres modules ne voient qu'une API REST (couche anti-corruption).
- (−) Deuxième JDK et deuxième image Docker à maintenir.
- (−) Spring Boot 2.7 ne reçoit plus de correctifs open source, à ma connaissance.
- (−) Écart avec l'énoncé (« Java 17 ou supérieur ») pour ce module.
- (−) Les bibliothèques de résilience et de traçabilité du bridge doivent rester compatibles Java 8 (par exemple Resilience4j 1.7.x : la série 2.x demande Java 17, à vérifier).

## À valider

- [ ] Le test Java 8 réussit (`search` ferroviaire depuis `lot0/corba-client`).
- [ ] L'enseignant accepte l'écart à « Java 17 ou supérieur » pour le bridge CORBA.
- [ ] Les bibliothèques prévues (OpenTelemetry, résilience) fonctionnent en Java 8.

## À revoir si

L'option 2 devient démontrablement fiable (dépendance maintenue fournissant `javax.rmi.CORBA`).