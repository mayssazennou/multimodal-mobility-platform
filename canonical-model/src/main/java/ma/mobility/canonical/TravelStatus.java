package ma.mobility.canonical;

/** PENDING -> CONFIRMED (tout est confirmé) ou CANCELLED (compensé) ; FAILED si la compensation elle-même échoue. */
public enum TravelStatus { PENDING, CONFIRMED, CANCELLED, FAILED }