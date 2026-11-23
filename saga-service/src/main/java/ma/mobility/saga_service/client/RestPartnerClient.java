package ma.mobility.saga.client;

import ma.mobility.saga_service.client.PartnerClient;
import ma.mobility.saga_service.domain.StepType;
import ma.mobility.saga_service.exception.PartnerRejectedException;
import ma.mobility.saga_service.exception.PartnerUncertainException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.function.Supplier;

public abstract class RestPartnerClient implements PartnerClient {

    /** Décrit l'API d'un bridge : chemins et noms de champs. */
    public record Api(String holdPath,
                      String confirmPath,
                      String cancelPath,
                      String resourceField) {}

    protected final RestClient http;
    private final StepType type;
    private final Api api;

    protected RestPartnerClient(StepType type, String baseUrl, Api api) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(10_000);
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.type = type;
        this.api = api;
    }

    @Override public StepType type() { return type; }

    @Override
    public String hold(String resourceId, int quantity, String key) {
        Map<String, Object> res = post(api.holdPath(), new Object[]{}, key,
                Map.of(api.resourceField(), resourceId, "seats", quantity));
        return field(res, "holdId", "id");
    }

    @Override
    public String confirm(String holdId, String key) {
        Map<String, Object> res = post(api.confirmPath(), new Object[]{holdId}, key, null);
        return field(res, "bookingRef", "reference", "id");
    }

    @Override
    public void cancel(String reference, String key) {
        post(api.cancelPath(), new Object[]{}, key, Map.of("reference", reference));
    }

    // ------------------------------------------------------------------

    protected Map<String, Object> post(String path, Object[] uriVars, String key, Map<String, Object> body) {
        return call(() -> {
            RestClient.RequestBodySpec spec = http.post()
                    .uri(path, uriVars)
                    .header("Idempotency-Key", key);
            if (body != null) {
                spec = spec.body(body);
            }
            Map<String, Object> res = spec.retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, r) -> {
                        throw new PartnerRejectedException(type + " a refusé " + path + " (" + r.getStatusCode() + ")");
                    })
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            return res == null ? Map.<String, Object>of() : res;
        });
    }

    /** 4xx = refus clair. Timeout / réseau / 5xx = on ne sait pas. */
    protected <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (PartnerRejectedException e) {
            throw e;
        } catch (ResourceAccessException | HttpServerErrorException e) {
            throw new PartnerUncertainException(type + " injoignable ou en erreur", e);
        }
    }

    /** Essaie plusieurs noms de champ possibles dans la réponse. */
    protected String field(Map<String, Object> res, String... names) {
        for (String n : names) {
            Object v = res.get(n);
            if (v != null) return v.toString();
        }
        throw new PartnerUncertainException(
                type + " : aucun champ " + String.join("/", names) + " dans la réponse " + res, null);
    }
}