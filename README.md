# Starter kit — Plateforme de mobilité multimodale

**Projet de fin de cycle ingénieur — Architectures distribuées (SOA · microservices · CORBA)**
Encadrement : *Pr. Mahmoud NASSAR*

---

Ce dépôt contient les **deux systèmes partenaires « legacy »** que vous devez
intégrer. Ils sont **fournis, fonctionnels et non modifiables** : vous ne disposez
que de leurs contrats publiés (IDL et XSD/WSDL). Tout le reste — microservices
internes, bridges de protocole, modèle canonique, orchestration de saga, API
Gateway — est à construire par vous.

```
                 À CONSTRUIRE (vous)              FOURNI (ce dépôt)
   client ──▶ API Gateway / BFF ──▶ bridges ──▶ ┌───────────────────────────┐
                                                 │ rail  : CORBA  (port 1050)│
                                                 │ air   : SOAP   (port 8081)│
                                                 └───────────────────────────┘
```

## Contenu

| Dossier | Rôle | Statut |
|---|---|---|
| `rail-operator-corba/` | Opérateur ferroviaire, serveur **CORBA** (JacORB) | fourni |
| `air-partner-soap/`    | Partenaire aérien, service **SOAP** (Spring WS)  | fourni |
| `docs/`                | Démarrage, contrats détaillés, sémantique de la saga | — |
| `ior/`                 | Reçoit l'IOR du serveur ferroviaire au démarrage | runtime |
| `docker-compose.yml`   | Démarre les deux systèmes | fourni |
| `verify.sh`            | Vérifie que le kit tourne (sans appel métier) | fourni |

## Prérequis

- **Docker** et **Docker Compose** (voie recommandée), ou
- **JDK 17+** et **Maven 3.9+** pour construire chaque module à la main.

> Au premier build, Maven télécharge JacORB, Spring Boot, etc. depuis Internet.
> Prévoir une connexion ouverte vers Maven Central.

## Démarrage

```bash
docker compose up --build
```

Puis, dans un autre terminal :

```bash
./verify.sh
```

Vous devez obtenir :

- `air`  → le WSDL répond sur <http://localhost:8081/ws/air.wsdl> ;
- `rail` → le fichier `ior/RailBooking.ior` est créé et le port `1050` écoute.

Détails et dépannage : **`docs/01-demarrage.md`**.

## Comment dialoguer avec les deux systèmes

- **Aérien (SOAP)** : récupérez le WSDL sur `http://localhost:8081/ws/air.wsdl`
  et générez votre client (wsimport/JAX-WS, Spring WS, ou tout outil SOAP).
- **Ferroviaire (CORBA)** : compilez l'IDL `rail-operator-corba/src/main/idl/rail.idl`
  côté client (JacORB), puis obtenez la référence de l'objet via l'**IOR**
  publié dans `ior/RailBooking.ior` :
  `orb.string_to_object(contenuDuFichierIor)` puis `RailBookingHelper.narrow(...)`.

Les deux contrats et leurs sémantiques (cycle `search → hold → confirm / cancel`,
idempotence, erreurs) sont décrits dans **`docs/02-contrats.md`** et
**`docs/03-semantique-saga.md`**.

## Règle d'or

> **Ne modifiez jamais les deux systèmes fournis.** Toute adaptation se fait
> dans *votre* couche d'intégration. Si vous ressentez le besoin de les changer,
> c'est le signe qu'un bridge ou une transformation manque de votre côté.

## Jeu de données

Figé et volontairement petit (voir `docs/02-contrats.md`). Un vol est
délibérément quasi complet (`AIR-CMN-MAD-1800`, 3 places) pour vous permettre de
provoquer un échec et de tester la **compensation** de votre saga.
