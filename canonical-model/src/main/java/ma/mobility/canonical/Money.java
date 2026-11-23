package ma.mobility.canonical;

import java.util.Objects;

/** Montant en unité mineure (centimes). Toujours un long : l'IDL ferroviaire est en 32 bits, le XSD aérien en 64. */
public record Money(long amountMinor, String currency) {
    public static final String MAD = "MAD";

    public Money {
        Objects.requireNonNull(currency, "currency");
        if (amountMinor < 0) throw new IllegalArgumentException("montant négatif");
    }

    public static Money mad(long amountMinor) { return new Money(amountMinor, MAD); }

    public Money plus(Money other) {
        if (!currency.equals(other.currency)) throw new IllegalArgumentException("devises différentes");
        return new Money(Math.addExact(amountMinor, other.amountMinor), currency);
    }
}
