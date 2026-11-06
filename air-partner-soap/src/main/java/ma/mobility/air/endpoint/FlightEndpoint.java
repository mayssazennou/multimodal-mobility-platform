package ma.mobility.air.endpoint;

import ma.mobility.air.service.FlightRepository;
import ma.mobility.air.ws.*;

import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

/**
 * Point de terminaison SOAP : relie les messages du contrat (air.xsd)
 * à la logique métier (FlightRepository).
 */
@Endpoint
public class FlightEndpoint {

    private static final String NS = "http://mobility.ma/air";

    private final FlightRepository repo;

    public FlightEndpoint(FlightRepository repo) {
        this.repo = repo;
    }

    @PayloadRoot(namespace = NS, localPart = "searchFlightsRequest")
    @ResponsePayload
    public SearchFlightsResponse search(@RequestPayload SearchFlightsRequest req) {
        SearchFlightsResponse resp = new SearchFlightsResponse();
        resp.getFlight().addAll(repo.search(req.getOrigin(), req.getDestination(), req.getDate()));
        return resp;
    }

    @PayloadRoot(namespace = NS, localPart = "holdFlightRequest")
    @ResponsePayload
    public HoldFlightResponse hold(@RequestPayload HoldFlightRequest req) {
        HoldFlightResponse resp = new HoldFlightResponse();
        resp.setHoldId(repo.hold(req.getFlightId(), req.getSeats()));
        return resp;
    }

    @PayloadRoot(namespace = NS, localPart = "confirmFlightRequest")
    @ResponsePayload
    public ConfirmFlightResponse confirm(@RequestPayload ConfirmFlightRequest req) {
        ConfirmFlightResponse resp = new ConfirmFlightResponse();
        resp.setBookingRef(repo.confirm(req.getHoldId()));
        return resp;
    }

    @PayloadRoot(namespace = NS, localPart = "cancelFlightRequest")
    @ResponsePayload
    public CancelFlightResponse cancel(@RequestPayload CancelFlightRequest req) {
        CancelFlightResponse resp = new CancelFlightResponse();
        resp.setStatus(repo.cancel(req.getReference()));
        return resp;
    }
}
