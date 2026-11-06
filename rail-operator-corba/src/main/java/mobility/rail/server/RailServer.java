package mobility.rail.server;

import mobility.rail.RailBooking;
import mobility.rail.RailBookingHelper;

import org.omg.CORBA.ORB;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.POAHelper;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Point d'entrée du serveur CORBA de l'opérateur ferroviaire.
 *
 * Au démarrage :
 *   1. initialise l'ORB JacORB ;
 *   2. active le servant RailBookingImpl sur le POA racine ;
 *   3. publie la référence de l'objet dans un fichier IOR partagé
 *      (/ior/RailBooking.ior par défaut) — c'est le moyen de bootstrap
 *      recommandé pour le bridge des étudiants ;
 *   4. attend les invocations (orb.run()).
 *
 * Variables d'environnement (fournies par docker-compose) :
 *   CORBA_ADVERTISED_HOST : hôte annoncé dans l'IOR (défaut "rail")
 *   CORBA_PORT            : port d'écoute IIOP        (défaut "1050")
 *   IOR_FILE             : chemin du fichier IOR     (défaut "/ior/RailBooking.ior")
 *
 * Encadrement : Pr. Mahmoud NASSAR
 */
public final class RailServer {

    public static void main(String[] args) throws Exception {

        String advertisedHost = env("CORBA_ADVERTISED_HOST", "rail");
        String port           = env("CORBA_PORT", "1050");
        String iorFile        = env("IOR_FILE", "/ior/RailBooking.ior");

        // --- Propriétés de l'ORB JacORB ---
        Properties props = new Properties();
        props.setProperty("org.omg.CORBA.ORBClass", "org.jacorb.orb.ORB");
        props.setProperty("org.omg.CORBA.ORBSingletonClass", "org.jacorb.orb.ORBSingleton");
        // Port d'écoute fixe de l'adaptateur d'objets (sinon aléatoire)
        props.setProperty("OAPort", port);
        // Adresse ANNONCÉE dans l'IOR : doit être joignable par le client (bridge).
        // Dans le réseau docker-compose, c'est le nom du service ("rail").
        props.setProperty("jacorb.ior_proxy_host", advertisedHost);
        props.setProperty("jacorb.ior_proxy_port", port);

        ORB orb = ORB.init(args, props);

        // --- POA racine ---
        POA rootPOA = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
        rootPOA.the_POAManager().activate();

        // --- Servant ---
        RailBookingImpl servant = new RailBookingImpl();
        org.omg.CORBA.Object ref = rootPOA.servant_to_reference(servant);
        RailBooking booking = RailBookingHelper.narrow(ref);

        // --- Publication de l'IOR dans le fichier partagé ---
        String ior = orb.object_to_string(booking);
        try {
            Path p = Paths.get(iorFile);
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            try (FileWriter w = new FileWriter(iorFile)) {
                w.write(ior);
            }
            System.out.println("[RailServer] IOR publié dans " + iorFile);
        } catch (Exception e) {
            System.out.println("[RailServer] IOR non écrit (" + e.getMessage()
                    + "). IOR ci-dessous :");
        }
        System.out.println("[RailServer] IOR = " + ior);
        System.out.println("[RailServer] Opérateur ferroviaire prêt — annonce "
                + advertisedHost + ":" + port + ". En attente d'invocations…");

        orb.run();
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.trim().isEmpty()) ? def : v;
    }

    private RailServer() {}
}
