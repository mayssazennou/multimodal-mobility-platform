package ma.mobility.saga_service.domain;

public enum SagaState {
    STARTED,
    HOLDING,
    HELD,
    CONFIRMING,
    CONFIRMED,      // fin nominale
    COMPENSATING,
    COMPENSATED,    // fin dégradée (tout annulé)
    FAILED_MANUAL   // la compensation n'a pas pu aboutir
}
