package ma.mobility.canonical;

/** Statut d'un maintien. Sérialisé en JSON par son nom ("HELD"), exactement comme la chaîne du contrat. */
public enum HoldStatus { HELD, CONFIRMED, CANCELLED }