package ma.mobility.rail.gateway;

import ma.mobility.rail.api.Dto;

import java.util.List;

/** Couche anti-corruption : aucun type CORBA ne sort de cette interface. Entrées : identifiants legacy bruts. */
public interface RailGateway {

    List<Dto.Offer> search(String origin, String destination, String date);

    /** @throws ma.mobility.rail.domain.OperatorRefusedException si plus de places ; ...UnavailableException si injoignable */
    Dto.Hold hold(String tripId, int seats);

    Dto.Booking confirm(String holdId);

    /** Idempotent : l'opérateur accepte un holdId ou un bookingRef, inconnu ou déjà annulé = pas d'erreur. */
    void cancel(String reference);
}