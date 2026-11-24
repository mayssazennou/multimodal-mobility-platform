package ma.mobility.saga_service.client;

import ma.mobility.saga_service.domain.StepType;

public interface PartnerClient {

    StepType type();

    /** Met la ressource de côté. Retourne le holdId. */
    String hold(String resourceId, int quantity, String idempotencyKey);

    /** Transforme le hold en réservation définitive. Retourne le bookingRef. */
    String confirm(String holdId, String idempotencyKey);

    /** Annule un holdId OU un bookingRef. Doit être idempotent : rien à annuler = succès. */
    void cancel(String reference, String idempotencyKey);
}
