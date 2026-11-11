package ma.mobility.lot0;

import mobility.rail.RailBooking;
import mobility.rail.RailBookingHelper;
import mobility.rail.Trip;
import org.omg.CORBA.ORB;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public class RailSmokeClient {

    public static void main(String[] args) throws Exception {
        String iorFile = args.length > 0 ? args[0] : "../../ior/RailBooking.ior";

        // 1. Dire à Java d'utiliser l'ORB JacORB
        Properties props = new Properties();
        props.setProperty("org.omg.CORBA.ORBClass", "org.jacorb.orb.ORB");
        props.setProperty("org.omg.CORBA.ORBSingletonClass", "org.jacorb.orb.ORBSingleton");

        ORB orb = ORB.init(new String[0], props);
        try {
            // 2. Lire l'IOR et le transformer en objet distant
            String ior = Files.readString(Paths.get(iorFile)).trim();
            org.omg.CORBA.Object obj = orb.string_to_object(ior);

            // 3. narrow = cast vérifié vers l'interface RailBooking
            RailBooking rail = RailBookingHelper.narrow(obj);

            // 4. Un appel distant, comme une méthode Java ordinaire
            Trip[] trips = rail.search("Casablanca", "Rabat", "2026-11-15");
            System.out.println(trips.length + " trajet(s) :");
            for (Trip t : trips) {
                System.out.printf("  %s  %s -> %s  départ %s  %d centimes  %d places%n",
                        t.tripId, t.origin, t.destination, t.departure,
                        t.priceMinor, t.seatsAvailable);
            }
        } finally {
            orb.shutdown(true);
        }
    }
}