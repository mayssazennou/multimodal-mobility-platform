package ma.mobility.rail.api;

import java.time.Instant;
import java.time.LocalDateTime;

/** Contrat REST commun (modèle canonique, version Java 8 : classes ordinaires, pas de records). */
public final class Dto {
    private Dto() { }

    public static class Money {
        public final long amountMinor;
        public final String currency;
        public Money(long amountMinor, String currency) {
            this.amountMinor = amountMinor;
            this.currency = currency;
        }
    }

    public static class Offer {
        public final String offerId;
        public final String mode;
        public final String providerRef;
        public final String origin;
        public final String destination;
        public final LocalDateTime departure;
        public final LocalDateTime arrival;
        public final Money price;
        public final int seatsAvailable;
        public Offer(String offerId, String mode, String providerRef, String origin, String destination,
                     LocalDateTime departure, LocalDateTime arrival, Money price, int seatsAvailable) {
            this.offerId = offerId; this.mode = mode; this.providerRef = providerRef;
            this.origin = origin; this.destination = destination;
            this.departure = departure; this.arrival = arrival;
            this.price = price; this.seatsAvailable = seatsAvailable;
        }
    }

    public static class HoldRequest {
        public String offerId;
        public int seats;
    }

    public static class Hold {
        public final String holdId;
        public final String mode;
        public final String providerHoldId;
        public final String offerId;
        public final int quantity;
        public final String status;
        public final Instant createdAt;
        /** Toujours null : l'opérateur legacy n'expose pas d'échéance. */
        public final Instant expiresAt;
        public Hold(String holdId, String mode, String providerHoldId, String offerId, int quantity,
                    String status, Instant createdAt, Instant expiresAt) {
            this.holdId = holdId; this.mode = mode; this.providerHoldId = providerHoldId;
            this.offerId = offerId; this.quantity = quantity; this.status = status;
            this.createdAt = createdAt; this.expiresAt = expiresAt;
        }
    }

    public static class BookingRequest {
        public String holdId;
    }

    public static class Booking {
        public final String bookingId;
        public final String mode;
        public final String providerBookingRef;
        public final String holdId;
        public final String status;
        public Booking(String bookingId, String mode, String providerBookingRef, String holdId, String status) {
            this.bookingId = bookingId; this.mode = mode;
            this.providerBookingRef = providerBookingRef; this.holdId = holdId; this.status = status;
        }
    }

    public static class ApiError {
        public final String code;
        public final String message;
        public final String correlationId;
        public ApiError(String code, String message, String correlationId) {
            this.code = code; this.message = message; this.correlationId = correlationId;
        }
    }
}