package ma.mobility.air_bridge.client;

import ma.mobility.airbridge.generated.*;
import org.springframework.ws.client.core.support.WebServiceGatewaySupport;

import java.util.List;

public class AirSoapClient extends WebServiceGatewaySupport {
    public String holdFlight(String flightId, int seats){
        //1. creer l'objet requte genere
        HoldFlightRequest req=new HoldFlightRequest();

        req.setFlightId(flightId);
        req.setSeats(seats);

        HoldFlightResponse resp=(HoldFlightResponse) getWebServiceTemplate().marshalSendAndReceive(req);

        return resp.getHoldId();
    }

    public List<Flight> searchFlights(String origin, String dest,String date){
        SearchFlightsRequest req=new SearchFlightsRequest();
        req.setOrigin(origin);
        req.setDestination(dest);
        req.setDate(date);

        SearchFlightsResponse resp=(SearchFlightsResponse) getWebServiceTemplate().marshalSendAndReceive(req);

        return resp.getFlight();
    }


    public String cancelFlight(String reference){
        CancelFlightRequest req=new CancelFlightRequest();
        req.setReference(reference);

        CancelFlightResponse resp=(CancelFlightResponse) getWebServiceTemplate().marshalSendAndReceive(req);
        return resp.getStatus();
    }

    public String confirmFlight(String holdId){
        ConfirmFlightRequest request=new ConfirmFlightRequest();
        request.setHoldId(holdId);
        ConfirmFlightResponse response=(ConfirmFlightResponse) getWebServiceTemplate().marshalSendAndReceive(request);
        return response.getBookingRef();
    }





}
