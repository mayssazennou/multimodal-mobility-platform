package ma.mobility.booking_service.controller;

import ma.mobility.canonical.Mode;

public record SegmentView(Mode mode, String status, String holdRef,
                          String bookingRef, Long priceMinor) {}
