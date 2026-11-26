# ADR 005 — Une base PostgreSQL par microservice

- **Statut** : adopté (provisoire : à confirmer avec une mesure de la mémoire)
- **Date** : 24/11/2026
- **Concerne** : `carshare-service`, `pricing-service`, `booking-service`, `saga-orchestrator`

## Contexte

Le sujet demande « une base de données par service ». Avec Docker Compose, on peut le faire de deux façons.
Contrainte forte : `docker compose up` doit démarrer toute la plateforme, y compris sur une machine que nous ne connaissons pas
(celle du correcteur). Or la plateforme lance déjà Kafka, Jaeger, les deux systèmes legacy et plusieurs services Java.

## Les deux options

**Option A : un seul conteneur PostgreSQL, une base et un utilisateur par service** (créés par un fichier `init.sql`).

- Avantages : une seule instance, donc moins de mémoire et moins de conteneurs à démarrer. C'est l'option qui a le plus de chances de démarrer partout.
- Limites :
  - L'isolation est **logique** seulement. Chaque service a sa base et son utilisateur, il ne peut donc pas lire les données des autres. Mais tous partagent le même processus, la même mémoire et le même disque : si PostgreSQL tombe, est surchargé ou est mis à jour, **tous les services sont touchés en même temps**.
  - Le fichier `init.sql` est commun aux deux membres de l'équipe. Il ne s'exécute qu'à la **première** création du volume : pour ajouter une base plus tard, il faut supprimer le volume (`docker compose down -v`).
  - Les services sont moins indépendants à déployer, sauvegarder et mettre à jour.
- Remarque : ce n'est pas une option « réservée au développement ». Beaucoup d'entreprises hébergent plusieurs bases sur une même instance pour réduire les coûts. C'est un compromis de coût, mais il donne moins d'indépendance.

**Option B : un conteneur PostgreSQL par service.**

- Avantages :
  - Isolation **physique** : une panne, une surcharge, une version ou un mot de passe ne concernent qu'un seul service.
  - Chaque propriétaire ajoute son bloc dans le compose sans toucher à celui de l'autre (pas de fichier partagé).
  - C'est l'approche d'un vrai déploiement.
- Limites :
  - Plus de mémoire : chaque PostgreSQL en consomme même au repos (mais reste peu coûteux; `docker stats` montre que carshare-db et saga-db consomment environ 36 Mio au repos chacun avec un plafond fixé par `mem_limit`).
  - Plus de conteneurs à démarrer, donc un risque plus grand d'échec sur une petite machine.

## Ce qui ne change pas

- Dans les deux cas, **un service n'accède jamais à la base d'un autre** : il passe par l'API ou par les événements. C'est cette règle qui garantit l'autonomie, plus que le nombre de conteneurs.
- Le code des services est identique : seule l'URL de connexion change. Passer d'une option à l'autre est donc peu coûteux.

## Décision

Nous prenons l'**option B** : un conteneur PostgreSQL par service, avec une limite de mémoire basse (`mem_limit`, `shared_buffers` et `max_connections` réduits) et un `healthcheck` pour que le service attende sa base.

**Plan de repli** : si `docker compose up` dépasse la mémoire ou échoue sur un clone propre, nous passons à l'option A (une instance, une base par service). Nous l'écrirons alors dans cet ADR.

## Conséquences et limites communes aux deux options

- La consommation de mémoire reste à mesurer sur la plateforme complète.
- Les mots de passe sont écrits en clair dans le compose : acceptable pour une démonstration, pas pour une production (il faudrait des secrets).
- Les tables sont créées automatiquement par Hibernate (`ddl-auto=update`). En production, on utiliserait des migrations versionnées (Flyway ou Liquibase).
- Pas de sauvegarde automatique des volumes.