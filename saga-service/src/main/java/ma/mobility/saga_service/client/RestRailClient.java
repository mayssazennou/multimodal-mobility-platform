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
                "/v1/bookings",        // non utilisé : confirm est redéfini
                "/v1/bookings/{ref}",  // non utilisé : cancel est redéfini
                "offerId"));
    }

    /** Rail : POST /v1/bookings avec le corps {holdId}. */
    @Override
    public String confirm(String holdId, String key) {
        Map<String, Object> res = post("/v1/bookings", new Object[]{}, key, Map.of("holdId", holdId));
        return field(res, "bookingId", "bookingRef", "reference", "id");
    }

    /** Rail : DELETE /v1/bookings/{ref}, accepte un holdId ou un bookingId. */
    @Override
    public void cancel(String reference, String key) {
        call(() -> {
            http.method(HttpMethod.DELETE)
                    .uri("/v1/bookings/{ref}", reference)
                    .header("Idempotency-Key", key)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, r) -> {
                        throw new PartnerRejectedException("RAIL a refusé le cancel (" + r.getStatusCode() + ")");
                    })
                    .toBodilessEntity();
            return null;
        });
    }
}