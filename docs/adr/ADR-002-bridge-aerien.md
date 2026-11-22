# ADR-002 — Bridge SOAP ↔ REST du partenaire aérien

**Statut** : accepté · **Date** : 2026-11-22

## Contexte
Le partenaire aérien n'expose qu'un service SOAP/WSDL, que nous ne pouvons pas modifier.
La saga et la gateway parlent REST/JSON et le modèle canonique (ADR-001). Il faut une
couche qui traduise sans que le moindre type SOAP ne sorte vers le reste de la plateforme.

## Décision
Un microservice Spring Boot `air-bridge`, structuré en couches :

| Couche | Rôle | Voit les types générés ? |
|---|---|---|
| `AirController` | contrat REST v1 (ADR-001) | non |
| `AirService` | orchestration, traduction des erreurs, idempotence | non (via le mapper) |
| `FlightMapper` | `Flight` → `Offer` canonique | oui |
| `AirSoapClient` | appels SOAP (`WebServiceGatewaySupport`) | oui |

- **Classes SOAP générées depuis le WSDL** (plugin `jaxb2-maven-plugin`, `sourceType=wsdl`),
  dans `ma.mobility.airbridge.generated`. Le WSDL est copié dans
  `src/main/resources/wsdl/air.wsdl` : le build ne dépend pas du partenaire et le contrat est versionné.
- **Erreurs à deux classes** (ADR-001, règle 4) :
    - SOAP Fault → `OperatorRefusedException` → HTTP 409 `NOT_AVAILABLE`
    - panne réseau/HTTP, réponse vide ou invalide → `OperatorUnavailableException` → HTTP 503 `PROVIDER_UNAVAILABLE`
    - entrée invalide → HTTP 400 `INVALID_REQUEST` (absent de l'ADR-001, ajouté)
    - le `faultstring` n'est jamais lu.
- **`cancel` idempotent par nature** : `BOOKING_CANCELLED` et `NOT_FOUND` sont tous deux un succès,
  réponse `204` ; un statut inattendu est journalisé (`WARN`) puis traité en 503.
- **Fault sur `cancel` → 409** (exception discutable à la règle 4) : le contrat ne prévoit pas
  de Fault métier sur cancel, un Fault y est donc plutôt une erreur interne du partenaire.
  Risque : la saga y voit un refus définitif et pourrait abandonner une compensation.
  **[À TRANCHER]** : garder 409, ou passer à 503 pour que la saga réessaie (recommandé).
- **Timeouts** sur le client SOAP : connexion 2 s, lecture 3 s. Sans eux, un partenaire figé
  bloquerait le bridge sans lever d'exception, et le 503 comme le futur circuit breaker ne se
  déclencheraient jamais. Un dépassement donne une `WebServiceIOException`, traduite en 503.
- **Champs canoniques ajoutés** (non fixés par l'ADR-001) : `Hold(holdId, offerId, mode)`,
  `Booking(bookingRef, mode)`, `ApiError(code, message, correlationId)`.
- **Pas de base relationnelle** : le bridge est un traducteur sans état métier. Il utilise
  seulement Redis pour l'idempotence (ADR-003).

## Alternatives écartées
- **Classes SOAP écrites à la main** : risque de non-conformité au WSDL, à refaire si le contrat change.
- **Génération depuis l'URL du WSDL** : le build échoue partenaire éteint ou dans le build Docker.

## Conséquences
- Un changement du contrat aérien n'impacte que le client et le mapper.
- La saga ne manipule qu'`Offer`, `Hold`, `Booking` et deux familles d'erreurs.

## Limites
- Dates traitées en `LocalDateTime` sans fuseau (hypothèse de l'ADR-001, confirmée sur la sortie réelle du SOAP).
- Pas encore de circuit breaker ni de traçabilité OpenTelemetry (`correlationId` provisoire en UUID).