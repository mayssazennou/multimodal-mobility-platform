package ma.mobility.booking_service.controller;

import java.time.Instant;
import java.util.List;

public record TravelBookingView(String id, String travelerId, String status,
                                Long totalAmountMinor, String currency,
                                String failureReason, Instant createdAt,
                                Instant updatedAt, List<SegmentView> segments) {}
