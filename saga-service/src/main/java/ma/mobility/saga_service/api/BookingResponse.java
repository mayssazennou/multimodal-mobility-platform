package ma.mobility.saga_service.api;

import ma.mobility.saga_service.entities.SagaEntity;

import java.util.List;
import java.util.UUID;

public record BookingResponse(UUID sagaId, String state, String failureReason, List<StepView> steps) {

    public record StepView(String partner, String phase, String status, String reference, String error) {}

    public static BookingResponse from(SagaEntity s) {
        return new BookingResponse(
                s.getId(),
                s.getState().name(),
                s.getFailureReason(),
                s.getSteps().stream()
                        .map(st -> new StepView(
                                st.getType().name(),
                                st.getPhase().name(),
                                st.getStatus().name(),
                                st.getExternalRef(),
                                st.getLastError()))
                        .toList());
    }
}
