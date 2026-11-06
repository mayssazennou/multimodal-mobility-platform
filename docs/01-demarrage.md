# 01 — Démarrage et dépannage

## Lancer les deux systèmes

```bash
docker compose up --build
```

Le premier build télécharge les dépendances (quelques minutes). Vous verrez
notamment dans les logs :

```
rail-operator-corba | [RailBooking] inventaire chargé : 7 trajet(s)
rail-operator-corba | [RailServer] IOR publié dans /ior/RailBooking.ior
rail-operator-corba | [RailServer] Opérateur ferroviaire prêt — annonce rail:1050…
air-partner-soap    | ... Started AirApplication ...
```

## Vérifier sans écrire de code

```bash
./verify.sh
```

Contrôle le WSDL aérien, la présence de l'IOR ferroviaire et l'écoute du
port 1050. Aucun appel métier n'est fait (c'est à vous de l'écrire).

Manuellement :

```bash
curl -s http://localhost:8081/ws/air.wsdl | head         # WSDL aérien
cat ior/RailBooking.ior                                  # IOR ferroviaire
```

## Construire sans Docker (optionnel)

```bash
# Ferroviaire (produit un jar exécutable autonome)
cd rail-operator-corba && mvn -DskipTests package
java -jar target/rail-operator-corba-1.0.0-jar-with-dependencies.jar

# Aérien
cd air-partner-soap && mvn -DskipTests package
java -jar target/air-partner-soap-1.0.0.jar
```

Hors Docker, le serveur ferroviaire écrit l'IOR dans `/ior/RailBooking.ior`.
Donnez un autre chemin via la variable `IOR_FILE`, et ajustez
`CORBA_ADVERTISED_HOST=localhost` :

```bash
IOR_FILE=./RailBooking.ior CORBA_ADVERTISED_HOST=localhost CORBA_PORT=1050 \
  java -jar target/rail-operator-corba-1.0.0-jar-with-dependencies.jar
```

## Où placer VOS services

Ajoutez vos microservices, bridges et BFF sur le même réseau Docker
`mobilite-net`. Deux options :

1. **Étendre ce `docker-compose.yml`** avec vos propres services (recommandé) ;
2. ou un compose séparé déclarant le réseau comme externe :

```yaml
networks:
  mobilite-net:
    external: true
```

Vos services joignent alors l'aérien à `http://air:8080/ws` et le ferroviaire
à l'adresse annoncée dans l'IOR (`rail:1050`).

## Dépannage

**Le bridge CORBA n'arrive pas à joindre le serveur ferroviaire.**
C'est le piège classique de CORBA : l'IOR contient une **adresse** (`rail:1050`)
qui doit être joignable *depuis le client*. Règle :

- bridge dans le réseau `mobilite-net` → laissez `CORBA_ADVERTISED_HOST=rail` ;
- bridge sur l'hôte (hors Docker) → démarrez avec `CORBA_ADVERTISED_HOST=localhost`
  (le port 1050 est déjà publié sur l'hôte).

**`org.omg` introuvable à la compilation du client.** Depuis Java 11, l'API CORBA
ne fait plus partie du JDK : ajoutez les dépendances JacORB
(`org.jacorb:jacorb` et `org.jacorb:jacorb-omgapi`) à *votre* projet client.

**Le WSDL ne répond pas.** Vérifiez que le conteneur `air` est bien démarré
(`docker compose logs air`) et l'URL exacte : `http://localhost:8081/ws/air.wsdl`.

**Le fichier IOR est vide / absent.** Regardez `docker compose logs rail` :
l'IOR est aussi imprimé dans les logs au démarrage.
