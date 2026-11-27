package ma.mobility.carshare;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rejoue le scénario du contrat commun v1 contre le service (base H2 en mémoire, jeu de données du DataSeeder). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:carshare-test",
        "spring.datasource.username=sa",
        "spring.datasource.password="})
@AutoConfigureMockMvc
class CarshareContractTest {

    @Autowired MockMvc mvc;

    private int seats(String offerId) throws Exception {
        String body = mvc.perform(get("/v1/offers?origin=Marrakech&destination=Marrakech&date=2026-11-15"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        java.util.List<Integer> r = JsonPath.read(body, "$[?(@.offerId=='" + offerId + "')].seatsAvailable");
        return r.get(0);
    }

    private MvcResult hold(String offerId, int n, String key) throws Exception {
        var req = post("/v1/holds").contentType(MediaType.APPLICATION_JSON)
                .content("{\"offerId\":\"" + offerId + "\",\"seats\":" + n + "}");
        if (key != null) req.header("Idempotency-Key", key);
        return mvc.perform(req).andReturn();
    }

    private void cancel(String ref) throws Exception {
        mvc.perform(post("/v1/cancellations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reference\":\"" + ref + "\"}")).andExpect(status().isNoContent());
    }

    @Test
    void searchReturnsCanonicalOffers() throws Exception {
        mvc.perform(get("/v1/offers?origin=Marrakech&destination=Marrakech&date=2026-11-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].offerId").value("CAR:CAR-MAR-MAR-0900"))
                .andExpect(jsonPath("$[0].mode").value("CAR"))
                .andExpect(jsonPath("$[0].price.currency").value("MAD"))
                .andExpect(jsonPath("$[0].departure").value("2026-11-15T09:00:00"));
    }

    @Test
    void holdConfirmCancelCycleIsIdempotentAndRestoresStock() throws Exception {
        String offer = "CAR:CAR-MAR-MAR-0900";
        int before = seats(offer);

        MvcResult h = hold(offer, 1, null);
        assertEquals(201, h.getResponse().getStatus());
        String holdId = JsonPath.read(h.getResponse().getContentAsString(), "$.holdId");
        assertEquals(before - 1, seats(offer));

        String b = mvc.perform(post("/v1/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holdId\":\"" + holdId + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andReturn().getResponse().getContentAsString();
        String bookingId = JsonPath.read(b, "$.bookingId");

        cancel(bookingId);
        cancel(bookingId);              // rejeu : toujours 204
        cancel("CAR:inconnu");          // inconnu : 204 aussi
        assertEquals(before, seats(offer));   // restitué une seule fois
    }

    @Test
    void cancelOnHoldReleasesStock() throws Exception {
        String offer = "CAR:CAR-MAR-MAR-0900";
        int before = seats(offer);
        String holdId = JsonPath.read(hold(offer, 2, null).getResponse().getContentAsString(), "$.holdId");
        cancel(holdId);
        cancel(holdId);
        assertEquals(before, seats(offer));
    }

    @Test
    void tooManySeatsIsBusinessRefusal409() throws Exception {
        MvcResult r = hold("CAR:CAR-MAR-MAR-0900", 1000, null);
        assertEquals(409, r.getResponse().getStatus());
        assertEquals("NOT_AVAILABLE", JsonPath.read(r.getResponse().getContentAsString(), "$.code"));
    }

    @Test
    void confirmUnknownHoldIs409() throws Exception {
        mvc.perform(post("/v1/bookings").contentType(MediaType.APPLICATION_JSON).content("{\"holdId\":\"CAR:inconnu\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_AVAILABLE"));
    }

    @Test
    void sameIdempotencyKeyReturnsSameHoldWithoutSecondReservation() throws Exception {
        String offer = "CAR:CAR-MAR-MAR-0900";
        int before = seats(offer);
        String a = JsonPath.read(hold(offer, 1, "k-1").getResponse().getContentAsString(), "$.holdId");
        String b = JsonPath.read(hold(offer, 1, "k-1").getResponse().getContentAsString(), "$.holdId");
        assertEquals(a, b);
        assertEquals(before - 1, seats(offer));
        cancel(a);
    }

    @Test
    void foreignPrefixAndBadSeatsAre400() throws Exception {
        assertEquals(400, hold("RAIL:RAIL-CAS-RAB-0900", 1, null).getResponse().getStatus());
        assertEquals(400, hold("CAR:CAR-MAR-MAR-0900", 0, null).getResponse().getStatus());
    }
}
