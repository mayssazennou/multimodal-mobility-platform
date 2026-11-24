package ma.mobility.saga_service.client;

import ma.mobility.saga_service.domain.StepType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RestCarClient extends ma.mobility.saga.client.RestPartnerClient {
    public RestCarClient(@Value("${bridges.car.url}") String url) {
        super(StepType.CAR, url, new Api(
                "/v1/holds",
                "/v1/holds/{holdId}/confirm",
                "/v1/cancellations",
                "offerId"));
    }
}