package ma.mobility.air.service;

import ma.mobility.air.ws.Flight;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logique métier du partenaire aérien. Mêmes sémantiques que l'opérateur
 * ferroviaire pour permettre une saga homogène : search -> hold -> confirm,
 * cancel idempotent (holdId ou bookingRef).
 *
 * État en mémoire ; inventaire chargé depuis flights.json (classpath).
 */
@Service
public class FlightRepository {

    private static final long HOLD_TTL_SECONDS = 600;

    /** Vol + nombre de places restantes (mutable). */
    private static final class Inv {
        final String id, origin, destination, departure, arrival;
        final long priceMinor;
        int seats;
        Inv(String id, String origin, String destination, String departure,
            String arrival, long priceMinor, int seats) {
            this.id = id; this.origin = origin; this.destination = destination;
            this.departure = departure; this.arrival = arrival;
            this.priceMinor = priceMinor; this.seats = seats;
        }
    }

    private record Hold(String flightId, int seats, Instant expiresAt) {}
    private record Booking(String flightId, int seats) {}

    private final ConcurrentHashMap<String, Inv>     inventory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Hold>    holds     = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Booking> bookings  = new ConcurrentHashMap<>();

    @PostConstruct
    void load() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("flights.json")) {
            if (in == null) { log("flights.json introuvable"); return; }
            String json = new String(in.readAllBytes());
            Matcher obj = Pattern.compile("\\{[^}]*}").matcher(json);
            while (obj.find()) {
                String o = obj.group();
                Inv f = new Inv(str(o, "flightId"), str(o, "origin"), str(o, "destination"),
                        str(o, "departure"), str(o, "arrival"),
                        num(o, "priceMinor"), (int) num(o, "seatsAvailable"));
                inventory.put(f.id, f);
            }
            log("inventaire chargé : %d vol(s)", inventory.size());
        } catch (Exception e) {
            log("ERREUR chargement flights.json : " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------

    public synchronized List<Flight> search(String origin, String destination, String date) {
        sweep();
        List<Flight> out = new ArrayList<>();
        for (Inv f : inventory.values()) {
            boolean okO = blank(origin)      || f.origin.equalsIgnoreCase(origin.trim());
            boolean okD = blank(destination) || f.destination.equalsIgnoreCase(destination.trim());
            boolean okDate = blank(date)     || f.departure.startsWith(date.trim());
            if (okO && okD && okDate) out.add(toFlight(f));
        }
        log("search(%s,%s,%s) -> %d", origin, destination, date, out.size());
        return out;
    }

    public synchronized String hold(String flightId, int seats) {
        sweep();
        if (seats <= 0) throw new NotAvailableException("Nombre de places invalide : " + seats);
        Inv f = inventory.get(flightId);
        if (f == null) throw new NotAvailableException("Vol inconnu : " + flightId);
        if (f.seats < seats)
            throw new NotAvailableException("Plus assez de places sur " + flightId
                    + " (demandé " + seats + ", disponible " + f.seats + ")");
        f.seats -= seats;
        String holdId = "HOLD-AIR-" + UUID.randomUUID();
        holds.put(holdId, new Hold(flightId, seats,
                Instant.now().plus(HOLD_TTL_SECONDS, ChronoUnit.SECONDS)));
        log("hold(%s,%d) -> %s", flightId, seats, holdId);
        return holdId;
    }

    public synchronized String confirm(String holdId) {
        sweep();
        Hold h = holds.remove(holdId);
        if (h == null) throw new NotAvailableException("Maintien inconnu ou expiré : " + holdId);
        String ref = "BK-AIR-" + UUID.randomUUID();
        bookings.put(ref, new Booking(h.flightId(), h.seats()));
        log("confirm(%s) -> %s", holdId, ref);
        return ref;
    }

    /** Idempotent : holdId ou bookingRef ; inconnu = succès. Renvoie un statut. */
    public synchronized String cancel(String reference) {
        if (reference == null) return "IGNORED";
        Hold h = holds.remove(reference);
        if (h != null) { release(h.flightId(), h.seats()); log("cancel(%s) hold libéré", reference); return "HOLD_RELEASED"; }
        Booking b = bookings.remove(reference);
        if (b != null) { release(b.flightId(), b.seats()); log("cancel(%s) booking annulé", reference); return "BOOKING_CANCELLED"; }
        log("cancel(%s) rien à annuler", reference);
        return "NOT_FOUND";
    }

    // ------------------------------------------------------------------

    private void release(String flightId, int seats) {
        Inv f = inventory.get(flightId);
        if (f != null) f.seats += seats;
    }

    private void sweep() {
        Instant now = Instant.now();
        holds.forEach((id, h) -> {
            if (h.expiresAt().isBefore(now)) {
                holds.remove(id);
                release(h.flightId(), h.seats());
                log("hold %s expiré -> places restituées", id);
            }
        });
    }

    private static Flight toFlight(Inv f) {
        Flight fl = new Flight();
        fl.setFlightId(f.id);
        fl.setOrigin(f.origin);
        fl.setDestination(f.destination);
        fl.setDeparture(f.departure);
        fl.setArrival(f.arrival);
        fl.setPriceMinor(f.priceMinor);
        fl.setSeatsAvailable(f.seats);
        return fl;
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String str(String o, String k) {
        Matcher m = Pattern.compile("\"" + k + "\"\\s*:\\s*\"([^\"]*)\"").matcher(o);
        return m.find() ? m.group(1) : "";
    }
    private static long num(String o, String k) {
        Matcher m = Pattern.compile("\"" + k + "\"\\s*:\\s*(-?\\d+)").matcher(o);
        return m.find() ? Long.parseLong(m.group(1)) : 0L;
    }
    private static void log(String fmt, Object... a) {
        System.out.println("[AirPartner] " + String.format(fmt, a));
    }
}
