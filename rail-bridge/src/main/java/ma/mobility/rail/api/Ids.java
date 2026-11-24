package ma.mobility.rail.api;

/** Identifiants canoniques = "RAIL:" + identifiant legacy. Accepte aussi l'identifiant brut en entrée. */
final class Ids {
    static final String PREFIX = "RAIL:";

    private Ids() { }

    static String raw(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("identifiant manquant");
        }
        if (id.startsWith(PREFIX)) {
            return id.substring(PREFIX.length());
        }
        if (id.startsWith("AIR:") || id.startsWith("CAR:")) {
            throw new IllegalArgumentException("identifiant d'un autre opérateur : " + id);
        }
        return id;
    }
}