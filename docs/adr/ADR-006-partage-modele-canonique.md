# ADR 0002 — Comment les services utilisent le modèle canonique

- **Statut** : accepté
- **Date** : 24/11/2026
- **Concerne** : les service `carshare-service`, `api-gateway`...

## Contexte

Le modèle canonique (`Offer`, `Hold`, `Booking`, `Money`, `ApiError`...) est le pivot de la plateforme : la saga et la gateway ne voient jamais de `Trip` CORBA ni de `Flight` SOAP. Il est écrit en Java dans le projet Maven `canonical-model` (17 classes). Chaque service doit parler le même JSON que ce modèle. Le `rail-bridge` fait exception : il est en Java 8 et ne peut pas utiliser ce module (il recopie les mêmes champs).

## Les deux options

**Option A : dépendance Maven.** On installe `canonical-model` (`mvn install`) puis on le déclare dans le `pom.xml` du service.

- Avantages :
  - Une seule copie du modèle : si un champ change, il change pour tout le monde.
  - Le code du service reste propre : les 17 classes ne sont pas mélangées à sa logique.
- Limites :
  - En développement local (hors Docker), il faut relancer `mvn -f canonical-model/pom.xml install` après chaque `git clone` et chaque modification du modèle. Si on l'oublie, Maven ne trouve pas le module, ou utilise une ancienne version (erreurs déroutantes).
  - Le Dockerfile est un peu plus long et doit être construit depuis la **racine** du dépôt (le module doit être copié dans l'image de build). Quand le modèle change, tous les services se reconstruisent.
  - Le README doit expliquer cette étape.

**Option B : copier les fichiers dans chaque service.**

- Avantages : aucune configuration, aucun `install`, un Dockerfile simple. C'est la solution la plus rapide.
- Limites :
  - Le modèle est dupliqué dans chaque service : le code du service est moins lisible.
  - **Risque principal : les copies divergent.** Si un champ change, il faut le modifier à plusieurs endroits. Un oubli ne se voit pas à la compilation : l'erreur n'apparaît qu'à l'exécution, quand un service lit mal le JSON d'un autre.

## Ce qui ne change pas

- La vraie référence est le **contrat JSON** (`contrats.md`), pas le code Java. Le `rail-bridge` le prouve : il fonctionne avec ses propres classes. Le script `demo/conformite.sh` vérifie que chaque service respecte ce contrat, quelle que soit l'option choisie.
- Dans les deux cas, les services dépendent du même modèle (si le modèle change, il faut adapter les services). Ce lien est acceptable ici car le modèle canonique est exigé par le sujet, il est petit et nous voulons qu'il reste stable.

## Décision

Nous prenons l'**option A** pour les services Java 25 : le modèle reste dans son propre module, et chaque service l'importe comme dépendance. La corvée de `mvn install` ne concerne que le développement local ; le build Docker, lui, fonctionne seul. Le README l'indiquera.

## Conséquences

- Le `README` explique : « avant de lancer un service hors Docker : `mvn -f canonical-model/pom.xml install -DskipTests` ».
- Toute modification du modèle passe par une pull request relue par l'autre membre de l'équipe (déjà la règle pour le contrat REST), et `demo/conformite.sh` est relancé ensuite.