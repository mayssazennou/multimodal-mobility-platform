package ma.mobility.canonical;

/** Vocabulaire d'erreurs unique, quel que soit le protocole d'origine. */
public enum ErrorCode {
    /** L'opérateur a répondu « non » (plus de places, maintien inconnu ou expiré). Refus métier, pas une panne. */
    NOT_AVAILABLE,
    /** On n'a pas pu parler à l'opérateur (connexion, délai dépassé). Seul cas compté par le circuit breaker. */
    PROVIDER_UNAVAILABLE,
    /** Requête invalide côté appelant. */
    INVALID_REQUEST
}