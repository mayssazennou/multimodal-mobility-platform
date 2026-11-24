package ma.mobility.carshare.domain;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;

/** Jeu de données initial (une seule fois). Paris/Paris : prolonge le vol Casablanca → Paris de la démo. */
@Component
public class DataSeeder implements ApplicationRunner {
    private final Repositories.OfferRepository offers;

    public DataSeeder(Repositories.OfferRepository offers) { this.offers = offers; }

    @Override
    public void run(ApplicationArguments args) {
        if (offers.count() > 0) return;
        LocalTime nine = LocalTime.of(9, 0), six = LocalTime.of(18, 0);
        offers.saveAll(List.of(
            new VehicleOfferEntity("CAR-PAR-PAR-0900", "Paris",      "Paris",      nine, six, 60000, 5),
            new VehicleOfferEntity("CAR-RBA-RBA-0900", "Rabat",      "Rabat",      nine, six, 45000, 8),
            new VehicleOfferEntity("CAR-CAS-CAS-0900", "Casablanca", "Casablanca", nine, six, 45000, 10),
            new VehicleOfferEntity("CAR-MAR-MAR-0900", "Marrakech",  "Marrakech",  nine, six, 40000, 6),
            new VehicleOfferEntity("CAR-RBA-CAS-0900", "Rabat",      "Casablanca", nine, six, 55000, 3)
        ));
    }
}
