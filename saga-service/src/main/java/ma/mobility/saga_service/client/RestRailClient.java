package ma.mobility.saga_service.client;

import ma.mobility.saga_service.domain.StepType;
import ma.mobility.saga_service.exception.PartnerRejectedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RestRailClient extends ma.mobility.saga.client.RestPartnerClient {

    public RestRailClient(@Value("${bridges.rail.url}") String url) {
        super(StepType.RAIL, url, new Api(
                "/v1/holds",
                "/v1/holds/{holdId}/confirm",
                "/v1/cancellations",
                "offerId"));
    }

}