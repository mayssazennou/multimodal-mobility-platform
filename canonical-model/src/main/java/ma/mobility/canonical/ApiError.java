package ma.mobility.canonical;

/** Corps d'erreur REST commun à tous les services. */
public record ApiError(ErrorCode code, String message, String correlationId) {
}