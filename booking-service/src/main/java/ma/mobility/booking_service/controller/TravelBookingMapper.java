package ma.mobility.booking_service.controller;

import ma.mobility.booking_service.entities.TravelBooking;
import org.springframework.stereotype.Component;

@Component
public class TravelBookingMapper {
    public TravelBookingView view(TravelBooking b) {
        return new TravelBookingView(
                b.getId(), b.getTravelerId(), b.getStatus().name(),
                b.getTotalAmountMinor(), b.getCurrency(), b.getFailureReason(),
                b.getCreatedAt(), b.getUpdatedAt(),
                b.getSegments().stream()
                        .map(s -> new SegmentView(s.getMode(), s.getStatus().name(),
                                s.getHoldRef(), s.getBookingRef(), s.getPriceMinor()))
                        .toList());
    }
}
