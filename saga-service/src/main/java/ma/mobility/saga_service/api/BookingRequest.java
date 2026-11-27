package ma.mobility.saga_service.api;


import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import ma.mobility.saga_service.domain.StepType;

public record BookingRequest(
        @NotBlank String railOfferId,
        @NotBlank String airOfferId,
        @NotBlank String carVehicleId,
        @Min(1) int seats,
        @NotBlank String travelerId
        ) {

    public String resourceFor(StepType type) {
        return switch (type) {
            case RAIL -> railOfferId;
            case AIR -> airOfferId;
            case CAR -> carVehicleId;
        };
    }
}
