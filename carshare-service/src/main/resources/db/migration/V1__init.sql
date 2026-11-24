CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE vehicle (
  id                   VARCHAR(36) PRIMARY KEY,
  plate                VARCHAR(20) NOT NULL UNIQUE,
  city                 VARCHAR(50) NOT NULL,
  category             VARCHAR(30) NOT NULL,
  price_per_hour_minor BIGINT NOT NULL
);

CREATE TABLE reservation (
  id               VARCHAR(36) PRIMARY KEY,
  hold_id          VARCHAR(36) NOT NULL UNIQUE,
  booking_ref      VARCHAR(36) UNIQUE,
  vehicle_id       VARCHAR(36) NOT NULL REFERENCES vehicle(id),
  start_at         TIMESTAMPTZ NOT NULL,
  end_at           TIMESTAMPTZ NOT NULL,
  status           VARCHAR(12) NOT NULL,
  hold_expires_at  TIMESTAMPTZ NOT NULL,
  idempotency_key  VARCHAR(100) UNIQUE,
  version          BIGINT NOT NULL DEFAULT 0,
  CHECK (end_at > start_at),
  CONSTRAINT no_overlap EXCLUDE USING gist (
    vehicle_id WITH =,
    tstzrange(start_at, end_at) WITH &&
  ) WHERE (status IN ('HELD','CONFIRMED'))
);

CREATE INDEX idx_reservation_expiry ON reservation(status, hold_expires_at);

INSERT INTO vehicle VALUES
 ('veh-1','12345-A-1','Casablanca','CITY',4000),
 ('veh-2','23456-B-6','Casablanca','SUV',7000),
 ('veh-3','34567-C-8','Rabat','CITY',4000);