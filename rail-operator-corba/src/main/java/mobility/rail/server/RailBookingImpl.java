package mobility.rail.server;

import mobility.rail.NotAvailable;
import mobility.rail.RailBookingPOA;
import mobility.rail.Trip;

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
 * Implémentation (servant) de l'interface CORBA RailBooking.
 *
 * État en mémoire :
 *   - inventory : trajets et places disponibles (chargés depuis trips.json)
 *   - holds     : maintiens temporaires (holdId -> Hold), avec expiration
 *   - bookings  : réservations fermes (bookingRef -> Booking)
 *
 * Cette classe est volontairement le SEUL élément "métier" du legacy.
 * Les étudiants ne la voient pas : ils ne disposent que du contrat IDL.
 *
 * Encadrement : Pr. Mahmoud NASSAR
 */
public class RailBookingImpl extends RailBookingPOA {

    /** Durée de vie d'un maintien avant expiration automatique. */
    private static final long HOLD_TTL_SECONDS = 600; // 10 minutes

    private final ConcurrentHashMap<String, Trip>    inventory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Hold>    holds     = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Booking> bookings  = new ConcurrentHashMap<>();

    /** Maintien temporaire (équivalent d'un record, en Java 8). */
    private static final class Hold {
        private final String tripId; private final int seats; private final Instant expiresAt;
        Hold(String tripId, int seats, Instant expiresAt) {
            this.tripId = tripId; this.seats = seats; this.expiresAt = expiresAt;
        }
        String tripId() { return tripId; }
        int seats() { return seats; }
        Instant expiresAt() { return expiresAt; }
    }

    /** Réservation ferme (équivalent d'un record, en Java 8). */
    private static final class Booking {
        private final String tripId; private final int seats;
        Booking(String tripId, int seats) { this.tripId = tripId; this.seats = seats; }
        String tripId() { return tripId; }
        int seats() { return seats; }
    }

    public RailBookingImpl() {
        loadInventory();
    }

    // ------------------------------------------------------------------
    //  Opérations CORBA
    // ------------------------------------------------------------------

    @Override
    public synchronized Trip[] search(String origin, String destination, String date) {
        sweepExpiredHolds();
        List<Trip> result = new ArrayList<>();
        for (Trip t : inventory.values()) {
            boolean okOrigin = origin == null || origin.trim().isEmpty()
                    || t.origin.equalsIgnoreCase(origin.trim());
            boolean okDest = destination == null || destination.trim().isEmpty()
                    || t.destination.equalsIgnoreCase(destination.trim());
            boolean okDate = date == null || date.trim().isEmpty()
                    || t.departure.startsWith(date.trim());
            if (okOrigin && okDest && okDate) {
                result.add(copyOf(t));
            }
        }
        log("search(%s, %s, %s) -> %d résultat(s)", origin, destination, date, result.size());
        return result.toArray(new Trip[0]);
    }

    @Override
    public synchronized String hold(String tripId, int seats) throws NotAvailable {
        sweepExpiredHolds();
        if (seats <= 0) {
            throw new NotAvailable("Nombre de places invalide : " + seats);
        }
        Trip t = inventory.get(tripId);
        if (t == null) {
            throw new NotAvailable("Trajet inconnu : " + tripId);
        }
        if (t.seatsAvailable < seats) {
            throw new NotAvailable("Plus assez de places sur " + tripId
                    + " (demandé " + seats + ", disponible " + t.seatsAvailable + ")");
        }
        t.seatsAvailable -= seats;                 // réservation provisoire
        String holdId = "HOLD-RAIL-" + UUID.randomUUID();
        holds.put(holdId, new Hold(tripId, seats,
                Instant.now().plus(HOLD_TTL_SECONDS, ChronoUnit.SECONDS)));
        log("hold(%s, %d) -> %s", tripId, seats, holdId);
        return holdId;
    }

    @Override
    public synchronized String confirm(String holdId) throws NotAvailable {
        sweepExpiredHolds();
        Hold h = holds.remove(holdId);
        if (h == null) {
            throw new NotAvailable("Maintien inconnu ou expiré : " + holdId);
        }
        String bookingRef = "BK-RAIL-" + UUID.randomUUID();
        bookings.put(bookingRef, new Booking(h.tripId(), h.seats()));
        log("confirm(%s) -> %s", holdId, bookingRef);
        return bookingRef;
    }

    @Override
    public synchronized void cancel(String bookingRef) {
        // IDEMPOTENT : accepte un holdId ou un bookingRef ; inconnu = succès silencieux.
        if (bookingRef == null) return;

        Hold h = holds.remove(bookingRef);
        if (h != null) {
            releaseSeats(h.tripId(), h.seats());
            log("cancel(%s) -> maintien libéré", bookingRef);
            return;
        }
        Booking b = bookings.remove(bookingRef);
        if (b != null) {
            releaseSeats(b.tripId(), b.seats());
            log("cancel(%s) -> réservation annulée", bookingRef);
            return;
        }
        log("cancel(%s) -> rien à annuler (idempotent)", bookingRef);
    }

    // ------------------------------------------------------------------
    //  Interne
    // ------------------------------------------------------------------

    private void releaseSeats(String tripId, int seats) {
        Trip t = inventory.get(tripId);
        if (t != null) {
            t.seatsAvailable += seats;
        }
    }

    private void sweepExpiredHolds() {
        Instant now = Instant.now();
        holds.forEach((id, h) -> {
            if (h.expiresAt().isBefore(now)) {
                holds.remove(id);
                releaseSeats(h.tripId(), h.seats());
                log("hold %s expiré -> places restituées", id);
            }
        });
    }

    private static Trip copyOf(Trip t) {
        return new Trip(t.tripId, t.origin, t.destination,
                t.departure, t.arrival, t.priceMinor, t.seatsAvailable);
    }

    /**
     * Charge l'inventaire depuis trips.json (classpath) sans dépendance externe :
     * petit parseur maison tolérant, suffisant pour ce jeu de données figé.
     */
    private void loadInventory() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("trips.json")) {
            if (in == null) {
                log("ATTENTION : trips.json introuvable, inventaire vide");
                return;
            }
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
            String json = new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            Matcher obj = Pattern.compile("\\{[^}]*}").matcher(json);
            while (obj.find()) {
                String o = obj.group();
                Trip t = new Trip(
                        str(o, "tripId"), str(o, "origin"), str(o, "destination"),
                        str(o, "departure"), str(o, "arrival"),
                        (int) num(o, "priceMinor"), (int) num(o, "seatsAvailable"));
                inventory.put(t.tripId, t);
            }
            log("inventaire chargé : %d trajet(s)", inventory.size());
        } catch (Exception e) {
            log("ERREUR de chargement de trips.json : " + e.getMessage());
        }
    }

    private static String str(String obj, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(obj);
        return m.find() ? m.group(1) : "";
    }

    private static long num(String obj, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+)").matcher(obj);
        return m.find() ? Long.parseLong(m.group(1)) : 0L;
    }

    private static void log(String fmt, Object... args) {
        System.out.println("[RailBooking] " + String.format(fmt, args));
    }
}
