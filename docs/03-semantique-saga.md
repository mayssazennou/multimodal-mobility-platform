# 03 — Sémantique du cycle de réservation et saga

Les deux systèmes exposent le même cycle de vie. Comprendre ses règles est la
clé de votre orchestration.

## Cycle de vie d'une ressource

```
   search ──▶ hold ──────▶ confirm         (chemin nominal)
                │  \
                │   └────▶ cancel(holdId)   libère un maintien non confirmé
                └────────▶ cancel(bookingRef) annule une réservation ferme
```

- **hold** décrémente *provisoirement* les places et renvoie un `holdId`.
  Un maintien **expire** automatiquement au bout de **10 minutes** : les places
  sont alors restituées et un `confirm` ultérieur échoue (`NotAvailable`).
- **confirm** transforme un maintien en réservation ferme (`bookingRef`).
- **cancel** est **idempotent** : il accepte un `holdId` *ou* un `bookingRef`,
  restitue les places, et ne lève jamais d'erreur si la référence est inconnue
  ou déjà annulée.

## Conséquences pour votre saga

La réservation multimodale engage plusieurs opérateurs. Schéma recommandé
(orchestration) :

1. **Phase hold** : `hold` sur chaque opérateur (train, avion, autopartage).
   Si un `hold` échoue → `cancel` sur les `holdId` déjà obtenus, puis abandon.
2. **Phase confirm** : une fois tous les maintiens obtenus, `confirm` chacun.
   Si un `confirm` échoue → `cancel` sur les `bookingRef` déjà confirmés **et**
   sur les `holdId` restants (compensation).

Points d'attention que l'évaluation vérifie :

- **Idempotence** côté saga aussi : un `cancel` rejoué (reprise, time-out
  réseau) ne doit pas casser l'état. Les systèmes fournis le tolèrent ; votre
  orchestrateur doit en profiter plutôt que de supposer un envoi unique.
- **Expiration des maintiens** : ne laissez pas traîner la phase confirm
  au-delà de la fenêtre de 10 minutes.
- **Cohérence** : un opérateur est localement transactionnel ; la fédération,
  elle, n'est cohérente qu'**à terme**, via la compensation. C'est tout l'écart
  entre une transaction ACID et une saga que vous devez montrer.

## Contrat de bridge suggéré (indicatif)

Pour homogénéiser, vos bridges peuvent exposer vers la saga une même interface
logique quel que soit le protocole sous-jacent :

```
search(origin, destination, date) -> [ Offre ]      // Offre = modèle canonique
hold(offreId, seats)              -> holdId
confirm(holdId)                   -> bookingRef
cancel(reference)                 -> void (idempotent)
```

Le bridge CORBA traduit vers `mobility::rail::RailBooking` ; le bridge SOAP vers
le service aérien ; le microservice d'autopartage l'implémente nativement.
C'est le rôle du **modèle canonique** : masquer que, dessous, l'un parle IIOP,
l'autre XML, le troisième JSON.
