CREATE TABLE travel_booking (
    id                  VARCHAR(64) PRIMARY KEY,
    traveler_id         VARCHAR(64) NOT NULL,
    status              VARCHAR(16) NOT NULL,
    total_amount_minor  BIGINT,
    currency            VARCHAR(3),
    failure_reason      VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE booking_segment (
    id              BIGSERIAL PRIMARY KEY,
    booking_id      VARCHAR(64) NOT NULL REFERENCES travel_booking(id),
    mode            VARCHAR(16) NOT NULL,
    status          VARCHAR(16) NOT NULL,
    hold_ref        VARCHAR(128),
    booking_ref     VARCHAR(128),
    price_minor     BIGINT,
    CONSTRAINT uq_segment_booking_mode UNIQUE (booking_id, mode)
);

CREATE TABLE processed_event (
    event_id      VARCHAR(64) PRIMARY KEY,
    processed_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_booking_traveler ON travel_booking(traveler_id);