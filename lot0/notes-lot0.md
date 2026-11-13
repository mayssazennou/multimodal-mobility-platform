# Notes du Lot 0 — prise en main du starter kit

Ce document consigne ce qu'on a observé en démarrant le kit et en explorant les deux contrats avant d'écrire la moindre ligne d'intégration. Il sert de matière première pour le rapport (section « points durs ») et pour les ADR. Chaque constat est marqué **observé** (vérifié sur le système qui tourne) ou **à vérifier** (lu dans le code ou la doc, pas encore testé).

## 1. Environnement de travail

On travaille sous **WSL2** (Ubuntu), avec le dépôt cloné dans le système de fichiers Linux (`~/multimodal-mobility-platform`) et non sous `/mnt/c/`, pour les performances de Docker et pour éviter les problèmes de fins de ligne. Les scripts du kit (`verify.sh`) ont été vérifiés en LF et rendus exécutables. L'authentification GitHub se fait par clé SSH sur le port 443, le port 22 étant bloqué. Les ressources de WSL2 sont bornées via `.wslconfig`. Un `.gitattributes` normalise les fins de ligne (PR #1).

Le kit démarre avec `docker compose up --build -d`, et `./verify.sh` renvoie tous les `[OK]`. Il fournit l'opérateur ferroviaire (CORBA/JacORB, 7 trajets, port 1050) et le partenaire aérien (SOAP/Spring WS, 6 vols, exposé sur l'hôte en 8081).

**JDK.** Deux versions de Java sont nécessaires : le JDK par défaut de la machine pour le reste du projet, et un **JDK 8** pour le client CORBA (voir section 4). Le JDK 8 est activé terminal par terminal (`JAVA_HOME` et `PATH`), sans changer le JDK par défaut. Maven lit `JAVA_HOME`, mais le plugin `exec` qui lance le compilateur IDL appelle `java` via le `PATH` : les deux doivent désigner la même version (`java -version` et `mvn -v`). Pour la livraison, c'est le `Dockerfile` de chaque module qui fixera le JDK, pas le terminal de la personne qui construit.

Point de référence Git : le tag `kit-origine` marque l'état d'origine du kit. La commande suivante doit rester vide pendant tout le projet, car les systèmes fournis ne se modifient pas :

```bash
git diff kit-origine -- rail-operator-corba air-partner-soap
```

## 2. Contrat SOAP : le WSDL publié diffère de l'énoncé

On a lu le WSDL réellement servi (`http://localhost:8081/ws/air.wsdl`) et on l'a comparé à l'annexe B de l'énoncé. Le WSDL fait foi (l'énoncé le dit lui-même), donc c'est lui qui sert de référence.

Le service est un `wsdl:service` dont l'endpoint est `http://localhost:8081/ws`. Le binding `AirPortSoap11` est en style **document / literal**, avec un `soapAction` vide partout. Le port type `AirPort` expose quatre opérations (`searchFlights`, `holdFlight`, `confirmFlight`, `cancelFlight`), chacune avec un input et un output. Le namespace est `http://mobility.ma/air`. Le XSD embarqué est une copie fidèle de `air.xsd`.

Deux écarts avec l'annexe B ont été relevés. Le premier : le WSDL ne déclare **aucun `wsdl:fault`**, alors que l'annexe évoque une faute `NotAvailable` sur `holdFlight`. Le second : dans le type `Flight`, `departure` est un `xsd:string` (ISO-8601) et non un `xsd:dateTime`, et un champ `arrival` existe, absent de l'extrait de l'énoncé.

## 3. Résultats du cycle SOAP (script `lot0/soap-cycle.sh`)

Le script envoie les requêtes avec `curl`, affiche chaque réponse avec son code HTTP, et extrait automatiquement le `holdId` et le `bookingRef` pour les étapes suivantes. Il enchaîne le cas nominal puis les cas d'échec.

Observé, sur le vol `AIR-CMN-ORY-1330` (Casablanca → Paris, 2026-11-15, 60 places, 160000 centimes) :

| Étape | Résultat |
|---|---|
| `searchFlights` | 1 vol, 60 places |
| `holdFlight` (2 places) | `holdId` de la forme `HOLD-AIR-<uuid>`, HTTP 200 |
| `searchFlights` après le hold | 58 places : le hold décrémente provisoirement |
| `confirmFlight` | `bookingRef` de la forme `BK-AIR-<uuid>`, HTTP 200 |
| `cancelFlight` (bookingRef) | `BOOKING_CANCELLED`, HTTP 200 |
| `searchFlights` après l'annulation | 60 places : les places sont restituées |
| `cancelFlight` rejoué 2 fois | `NOT_FOUND`, HTTP 200, sans erreur |
| `holdFlight` 4 places sur `AIR-CMN-MAD-1800` (3 places) | SOAP Fault, **HTTP 500**, `faultcode` `SOAP-ENV:Server`, `faultstring` « Plus assez de places sur AIR-CMN-MAD-1800 (demandé 4, disponible 3) » |
| `confirmFlight` avec un `holdId` inventé (`xxx`) | SOAP Fault, **HTTP 500**, `faultstring` « Maintien inconnu ou expiré : xxx » |

### Constats à retenir pour l'intégration

**Idempotence d'effet, pas de réponse.** Le premier `cancel` répond `BOOKING_CANCELLED`, les suivants répondent `NOT_FOUND`. Les places ne sont pas rendues deux fois (l'effet est idempotent), mais la réponse n'est pas identique. Le bridge SOAP devra traiter `NOT_FOUND` comme un succès de l'annulation et exposer à la saga un `cancel` canonique qui ne distingue pas les deux cas.

**Erreurs métier = SOAP Fault générique.** Les refus métier arrivent avec un `faultcode` `Server` et un `faultstring` en texte libre, sans code d'erreur ni type de faute déclaré dans le contrat. On ne veut pas dépendre du texte : le bridge traduira tout SOAP Fault métier en une erreur canonique unique `NOT_AVAILABLE`, en conservant le message d'origine comme détail. La cause se déduit de l'opération qui a échoué (un Fault sur `hold` signifie plus de places, un Fault sur `confirm` signifie maintien inconnu ou expiré). C'est cohérent avec CORBA, qui n'a de toute façon qu'une seule exception `NotAvailable`.

**Un refus métier a le même statut HTTP qu'une vraie panne (500).** Conséquence pour la résilience (Lot 3) : un circuit breaker qui compterait tous les 500 comme des échecs s'ouvrirait après quelques refus de places consécutifs, alors que le partenaire fonctionne. Il faudra distinguer « le partenaire a répondu non » (SOAP Fault métier, à ne **pas** compter comme échec) de « on n'a pas pu lui parler » (connexion refusée, timeout, réponse illisible, à compter comme échec).

**Expiration des maintiens : non testée.** Le test `confirmFlight` avec un `holdId` inventé a bien produit une faute « Maintien inconnu ou expiré », mais c'était un maintien inconnu, pas une vraie expiration. D'après `docs/03-semantique-saga.md`, un maintien expire au bout de 10 minutes et un `confirm` ultérieur échoue. On n'a pas attendu 10 minutes pour le vérifier.

## 4. CORBA : résultats du client de test

### 4.1 Java et JacORB (observé)

Le serveur ferroviaire est compilé et exécuté en **Java 8**, parce que JacORB 3.9 est une bibliothèque Java 8 dont l'ORB a besoin à l'exécution du paquet `javax.rmi.CORBA`, retiré du JDK depuis Java 11. Le sujet impose Java 17 ou plus pour ce qu'on construit, d'où un test préalable.

On a écrit un client minimal (`lot0/corba-client`, module Maven) :
- les stubs sont générés **au build** depuis l'IDL du kit : `exec-maven-plugin` lance `org.jacorb.idl.parser` dans un processus séparé (le compilateur appelle `System.exit`), ce qui produit 15 classes. L'IDL est référencé et non copié pour le lot 0 ; il faudra le copier dans le contexte Docker du futur bridge ;
- le client lit l'IOR dans `ior/RailBooking.ior`, initialise l'ORB JacORB (propriétés `org.omg.CORBA.ORBClass` et `ORBSingletonClass`), puis appelle `search("Casablanca", "Rabat", "2026-11-15")`.

| JDK | Génération des stubs | Compilation | `ORB.init` | `string_to_object` / `search` |
|---|---|---|---|---|
| 17 | OK | OK | OK | **Échec** : `NoClassDefFoundError: org.jacorb.orb.Reference` |
| 8 | OK | OK | OK | **OK** : 2 trajets, `RAIL-CAS-RAB-0730` et `RAIL-CAS-RAB-0900` (8000 centimes, 120 places) |
| 25 | non testé | non testé | non testé | non testé |

**Cause (échec sous Java 17).** Le message est trompeur : la classe `org.jacorb.orb.Reference` existe bien dans le jar. Dans les sources de JacORB 3.9, elle étend `javax.rmi.CORBA.Stub`, un paquet qui n'existe plus dans le JDK depuis Java 11, donc elle ne peut pas se charger. L'échec est local (avant tout appel réseau) et ne remet en cause ni le contrat IDL ni la génération des stubs. Le même échec est attendu en Java 25, **à vérifier**.

**À vérifier (lu dans les sources, pas testé).** Cinq classes de JacORB 3.9 importent `javax.rmi` : `Reference`, `ValueHandler`, `PortableRemoteObjectDelegateImpl`, `CDRInputStream` et `CDROutputStream`. Les flux CDR et `ValueHandler` semblent liés aux types « valeur » (RMI-IIOP), que cet IDL n'utilise pas, mais ça n'a pas été testé. Les pistes pour rester en Java 17 ou plus (dépendance tierce fournissant `javax.rmi.CORBA`, ou classe `Stub` écrite à la main) n'ont **pas été essayées** : elles ont été écartées au profit du module Java 8 isolé (ADR-0001).

**Pièges rencontrés (matière pour « points durs »).**
- `maven-compiler-plugin` 3.1, version par défaut, ignore `maven.compiler.release` et retombe sur Java 5 : il faut fixer la version 3.13.0.
- L'option `release` n'existe pas dans le compilateur du JDK 8 : on utilise `maven.compiler.source` et `maven.compiler.target`.
- `Files.readString` n'existe qu'à partir de Java 11 : le client utilise `Files.readAllBytes`.
- Après un changement de JDK, il faut un `mvn clean` : les classes compilées pour Java 17 ne se chargent pas sous Java 8.
- Les avertissements SLF4J au lancement sont sans gravité (aucun logger configuré, JacORB se tait).

**Conséquence (ADR-0001, acceptée).** Le module `bridge-rail` sera construit et exécuté en Java 8 (Spring Boot 2.7.x), dans son propre conteneur ; le reste de la plateforme reste en Java 17 ou plus. Les bibliothèques du bridge doivent rester compatibles Java 8 (par exemple Resilience4j 1.7.x, la série 2.x exigeant Java 17 : **à vérifier**, ainsi que OpenTelemetry).

### 4.2 IOR et réseau

**Observé.** L'IOR imprimé dans les logs encode l'IP `172.23.0.3` (résolution de l'hôte annoncé `rail`) et le port 1050. Un client qui tourne hors du réseau Docker ne peut donc pas joindre le serveur avec la configuration d'origine. Selon `docs/01-demarrage.md`, la règle est : bridge dans le réseau `mobilite-net` → `CORBA_ADVERTISED_HOST=rail` ; client sur l'hôte → `CORBA_ADVERTISED_HOST=localhost`.

**Observé.** Pour le client sur l'hôte, on change cette valeur sans toucher aux fichiers du kit, avec un fichier de surcharge `lot0/docker-compose.host.yml` :

```bash
docker compose -f docker-compose.yml -f lot0/docker-compose.host.yml up -d --force-recreate rail
```

Avec cette surcharge, le client lancé depuis l'hôte joint le serveur et `search` répond. Pour revenir à la configuration d'origine : `docker compose up -d --force-recreate rail`.

**À vérifier (lu dans le code).** Le serveur n'enregistre rien dans un service de nommage : il écrit son IOR dans `ior/RailBooking.ior` au démarrage (et l'imprime dans les logs). C'est un écart avec l'énoncé, qui mentionne un service de nommage. Conséquence probable : après un redémarrage du conteneur `rail`, l'IOR change et l'ancien devient invalide, donc le bridge devra savoir relire l'IOR (point de résilience à traiter au Lot 3).

### 4.3 Contrat IDL

`rail.idl` expose `search`, `hold`, `confirm` (qui lèvent `NotAvailable`) et `cancel` (idempotent, accepte un `holdId` ou un `bookingRef`, ne lève jamais d'erreur). Contrairement au SOAP, l'erreur métier est ici **typée** dans le contrat.

**Reste à tester côté CORBA.** Seul `search` a été exercé. À faire : `hold`, `confirm`, `cancel`, le cas d'échec `NotAvailable`, l'expiration du maintien, et le comportement du `cancel` rejoué.

**À prévoir pour le bridge.** Il faudra distinguer `NotAvailable` (erreur métier) des exceptions système CORBA (`COMM_FAILURE`, `TRANSIENT`, `OBJECT_NOT_EXIST`, timeout), qui sont des pannes techniques : même logique que la distinction refus métier / panne côté SOAP, pour les reprises et le circuit breaker. Les appels CORBA sont synchrones et bloquants : un timeout côté client sera nécessaire (propriétés de JacORB à identifier dans sa documentation).

## 5. Décisions d'architecture (ADR)

Écrit :
- **ADR-0001** : JDK 8 pour le module `bridge-rail` (acceptée). Points encore ouverts : accord de l'enseignant sur l'écart à « Java 17 ou supérieur », compatibilité Java 8 des bibliothèques de résilience et de traçabilité.

À documenter au fil du projet : référence au WSDL publié plutôt qu'à l'énoncé en cas de divergence ; traduction des erreurs legacy en une erreur canonique unique ; traitement de `NOT_FOUND` comme succès d'annulation ; distinction refus métier / panne pour les circuit breakers ; génération des stubs CORBA au build depuis l'IDL (et copie de l'IDL dans le contexte Docker du bridge) ; stratégie de récupération de l'IOR.