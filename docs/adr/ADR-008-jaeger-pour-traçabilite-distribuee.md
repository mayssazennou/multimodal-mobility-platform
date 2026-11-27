# ADR-008 : Jaeger comme backend de traçabilité distribuée

- **Statut** : accepté
- **Date** : 2026-11-25
- **Lié à** : exigence du sujet « traçabilité distribuée de bout en bout, à travers les trois protocoles »

## Contexte
 
Une réservation multimodale traverse l'api-gateway, le saga-orchestrator, les deux bridges (CORBA et SOAP), le carshare-service et le bus Kafka. En cas d'échec ou de lenteur, chercher la cause dans les journaux de plusieurs conteneurs est long et peu fiable.
 
Le sujet impose **OpenTelemetry** pour la traçabilité et la corrélation des journaux par identifiant de trace. Il n'impose pas de backend de stockage et de visualisation des traces : c'est le choix à documenter ici.
 
Contraintes qui pèsent sur ce choix :
 
- `docker compose up` doit démarrer toute la plateforme sur une machine propre, sans manipulation hors README ;
- la mémoire disponible est déjà un risque identifié (nombreux conteneurs, dont Kafka) ;
- le critère de réussite est concret : **une réservation = une seule trace visible dans une interface**, de la gateway jusqu'aux bridges et à carshare ;
- les services sont instrumentés par l'agent Java OpenTelemetry, qui exporte au format OTLP.

## Décision

Utiliser **Jaeger**, dans sa génération actuelle (construite sur le collecteur OpenTelemetry), déployé en un seul conteneur comme backend de traces dans le docker-compose global.

- Les services (agent OpenTelemetry) exportent leurs spans en OTLP vers Jaeger (ports 4317 en gRPC ou 4318 en HTTP).
- L'interface web de Jaeger (port 16686) sert à visualiser la trace de bout en bout lors de la démonstration.
- La gateway crée la trace ; l'en-tête `traceparent` est propagé vers la saga puis vers les bridges ; le `traceId` est inscrit dans les journaux de chaque service.

## Alternatives étudiées
 
| Option | Avantages | Raison de l'écarter |
|---|---|---|
| **Zipkin** | Très léger, un seul conteneur | Réception OTLP à vérifier ; interface moins riche pour lire une trace multi-services |
| **Grafana Tempo** | Corrélation traces, métriques et logs ; stockage durable | Au moins deux conteneurs (Tempo + Grafana) et plus de configuration, pour des besoins que le sujet n'exige pas |
| **SigNoz** | Traces, métriques et logs réunis | Pile lourde (base orientée colonnes), peu compatible avec un démarrage léger sur machine propre |
| **Solutions SaaS** (Datadog, Honeycomb) | Très complètes | Comptes externes : incompatible avec « le dépôt démarre avec une seule commande » |
 
## Conséquences
 
**Positives**
 
- Un seul conteneur, interface incluse, configuration minimale : faible risque pour `docker compose up` et faible empreinte mémoire.
- Réception OTLP native : l'agent OpenTelemetry exporte directement vers Jaeger.
- **Le choix est réversible à faible coût** : OpenTelemetry est indépendant du backend. Changer d'outil revient surtout à modifier l'endpoint d'export dans la configuration des agents, sans toucher au code des services.

**Compromis acceptés**
 
- Stockage **en mémoire** par défaut : les traces sont perdues au redémarrage du conteneur. Acceptable pour une démonstration ; pour conserver les traces, il faudrait brancher un stockage externe.
- La mémoire du conteneur Jaeger est plafonnée : lors de longues sessions de test, le volume de traces conservé reste limité.
- Faire évoluer la version de l'image consiste à modifier le tag dans le docker-compose et à retester sur clone propre ; cela ne remet pas en cause la décision.
- Jaeger ne traite que les **traces** (pas de métriques ni de journaux) : la corrélation avec les journaux se fait par le `traceId` écrit dans les logs, pas dans l'outil.
- L'agent Java ne connaît pas CORBA : l'appel IIOP du rail-bridge vers le serveur legacy n'apparaît pas comme span séparé, sauf si on l'entoure d'un span manuel (API OpenTelemetry). Au minimum, la trace montre le span REST du bridge et sa durée totale.
- La compatibilité de l'agent OpenTelemetry avec le rail-bridge en Java 8 (voir ADR-0001) reste à vérifier.
## Vérification
 
Test d'intégration commun : lancer une réservation complète et constater, dans Jaeger, **une seule trace** couvrant gateway, saga, bridges et carshare.