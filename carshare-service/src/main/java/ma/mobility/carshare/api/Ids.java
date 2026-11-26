package ma.mobility.carshare.api;

/** Identifiants canoniques = "CAR:" + identifiant brut. Accepte aussi l identifiant brut en entrée. */
public final class Ids {
    public static final String PREFIX = "CAR:";

    private Ids() { }

    public static String raw(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("identifiant manquant");
        if (id.startsWith(PREFIX)) return id.substring(PREFIX.length());
        if (id.startsWith("RAIL:") || id.startsWith("AIR:")) {
            throw new IllegalArgumentException("identifiant d un autre opérateur : " + id);
        }
        return id;
    }

    public static String canonical(String raw) { return PREFIX + raw; }
}
