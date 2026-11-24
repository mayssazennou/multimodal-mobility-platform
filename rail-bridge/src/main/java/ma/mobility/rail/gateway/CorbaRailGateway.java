package ma.mobility.rail.gateway;

import ma.mobility.rail.api.Dto;
import ma.mobility.rail.domain.OperatorRefusedException;
import ma.mobility.rail.domain.OperatorUnavailableException;
import mobility.rail.NotAvailable;
import mobility.rail.RailBooking;
import mobility.rail.RailBookingHelper;
import mobility.rail.Trip;
import org.omg.CORBA.ORB;
import org.omg.CORBA.SystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Client CORBA (JacORB) vers l'opérateur ferroviaire. Le contrat IDL fait foi.
 * Connexion paresseuse : l'IOR est lu au premier appel, et relu après une panne
 * (le serveur regénère son IOR à chaque démarrage).
 */
@Component
public class CorbaRailGateway implements RailGateway {

    private static final Logger log = LoggerFactory.getLogger(CorbaRailGateway.class);
    private static final String MODE = "RAIL";
    private static final String PREFIX = "RAIL:";

    /** Appel CORBA pouvant lever l'exception IDL NotAvailable. */
    private interface Call<T> {
        T run(RailBooking rail) throws NotAvailable;
    }

    private final Path iorFile;
    private final long connectTimeoutMs;
    private final long replyTimeoutMs;
    private ORB orb;
    private volatile RailBooking stub;

    public CorbaRailGateway(@Value("${rail.ior-file}") String iorFile,
                            @Value("${rail.connect-timeout-ms}") long connectTimeoutMs,
                            @Value("${rail.reply-timeout-ms}") long replyTimeoutMs) {
        this.iorFile = Paths.get(iorFile);
        this.connectTimeoutMs = connectTimeoutMs;
        this.replyTimeoutMs = replyTimeoutMs;
    }

    // ---------------------------------------------------------------- opérations

    @Override
    public List<Dto.Offer> search(String origin, String destination, String date) {
        Trip[] trips = call(rail -> rail.search(origin, destination, date));
        List<Dto.Offer> offers = new ArrayList<>();
        for (Trip t : trips) {
            offers.add(toOffer(t));
        }
        return offers;
    }

    @Override
    public Dto.Hold hold(String tripId, int seats) {
        String holdId = call(rail -> rail.hold(tripId, seats));
        log.info("hold OK tripId={} seats={} holdId={}", tripId, seats, holdId);
        return new Dto.Hold(PREFIX + holdId, MODE, holdId, PREFIX + tripId, seats, "HELD", Instant.now(), null);
    }

    @Override
    public Dto.Booking confirm(String holdId) {
        String ref = call(rail -> rail.confirm(holdId));
        log.info("confirm OK holdId={} bookingRef={}", holdId, ref);
        return new Dto.Booking(PREFIX + ref, MODE, ref, PREFIX + holdId, "CONFIRMED");
    }

    @Override
    public void cancel(String reference) {
        call(rail -> {
            rail.cancel(reference);
            return null;
        });
        log.info("cancel OK reference={}", reference);
    }

    // ---------------------------------------------------------------- mapping Trip -> Offer

    private static Dto.Offer toOffer(Trip t) {
        return new Dto.Offer(
                PREFIX + t.tripId, MODE, t.tripId, t.origin, t.destination,
                parse(t.departure), parse(t.arrival),
                new Dto.Money(t.priceMinor, "MAD"), t.seatsAvailable);
    }

    /** Format IDL : ISO-8601 sans fuseau, ex. 2026-11-15T07:30:00. */
    private static LocalDateTime parse(String iso) {
        return (iso == null || iso.trim().isEmpty()) ? null : LocalDateTime.parse(iso.trim());
    }

    // ---------------------------------------------------------------- plomberie CORBA

    private <T> T call(Call<T> call) {
        RailBooking rail = stub();
        try {
            return call.run(rail);
        } catch (NotAvailable e) {
            // Refus métier typé par l'IDL : pas une panne.
            throw new OperatorRefusedException(e.reason);
        } catch (SystemException e) {
            // COMM_FAILURE, TRANSIENT, TIMEOUT... : on oublie la référence, l'IOR sera relu au prochain appel.
            reset();
            throw new OperatorUnavailableException("CORBA " + e.getClass().getSimpleName(), e);
        }
    }

    private RailBooking stub() {
        RailBooking current = stub;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (stub == null) {
                try {
                    String ior = new String(Files.readAllBytes(iorFile), StandardCharsets.UTF_8).trim();
                    if (orb == null) {
                        orb = ORB.init(new String[0], orbProperties());
                    }
                    stub = RailBookingHelper.narrow(orb.string_to_object(ior));
                    log.info("Référence CORBA obtenue depuis {}", iorFile);
                } catch (IOException | SystemException e) {
                    throw new OperatorUnavailableException("IOR inaccessible (" + iorFile + ")", e);
                }
            }
            return stub;
        }
    }

    private synchronized void reset() {
        stub = null;
    }

    private Properties orbProperties() {
        Properties p = new Properties();
        p.setProperty("org.omg.CORBA.ORBClass", "org.jacorb.orb.ORB");
        p.setProperty("org.omg.CORBA.ORBSingletonClass", "org.jacorb.orb.ORBSingleton");
        // Noms de propriétés JacORB 3.x : à valider en provoquant un timeout (arrêter le conteneur rail).
        p.setProperty("jacorb.connection.client.connect_timeout", String.valueOf(connectTimeoutMs));
        p.setProperty("jacorb.connection.client.pending_reply_timeout", String.valueOf(replyTimeoutMs));
        return p;
    }

    @PreDestroy
    void shutdown() {
        if (orb != null) {
            orb.shutdown(true);
        }
    }
}