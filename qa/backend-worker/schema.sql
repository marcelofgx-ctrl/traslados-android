-- Quick Trip QA ONLY — SQLite / Cloudflare D1. Not a production migration.
-- This is NEVER to be executed against Supabase or any operational database.
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS qa_requests (
  id TEXT PRIMARY KEY NOT NULL,
  passenger_ref TEXT NOT NULL CHECK(length(passenger_ref) = 64),
  idempotency_key TEXT NOT NULL,
  payload_hash TEXT NOT NULL CHECK(length(payload_hash) = 64),
  driver_ref TEXT,
  state TEXT NOT NULL DEFAULT 'PENDIENTE' CHECK(state IN
    ('PENDIENTE','ACEPTADA','RECHAZADA','CANCELADA','EN_CAMINO','EN_PUNTO','EN_VIAJE','FINALIZADA')),
  version INTEGER NOT NULL DEFAULT 1 CHECK(version > 0),
  origin_label TEXT NOT NULL CHECK(length(origin_label) BETWEEN 1 AND 160),
  destination_label TEXT NOT NULL CHECK(length(destination_label) BETWEEN 1 AND 160),
  origin_lat REAL NOT NULL CHECK(origin_lat BETWEEN -35.5 AND -30),
  origin_lng REAL NOT NULL CHECK(origin_lng BETWEEN -59.5 AND -52),
  destination_lat REAL NOT NULL CHECK(destination_lat BETWEEN -35.5 AND -30),
  destination_lng REAL NOT NULL CHECK(destination_lng BETWEEN -59.5 AND -52),
  fare_uyu REAL NOT NULL CHECK(fare_uyu >= 0 AND fare_uyu <= 500000),
  last_actor_role TEXT NOT NULL CHECK(last_actor_role IN ('passenger','driver')),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  qa_only INTEGER NOT NULL DEFAULT 1 CHECK(qa_only = 1),
  UNIQUE(passenger_ref, idempotency_key)
);

CREATE INDEX IF NOT EXISTS qa_requests_pending_ix
  ON qa_requests(state, created_at DESC);
CREATE INDEX IF NOT EXISTS qa_requests_passenger_ix
  ON qa_requests(passenger_ref, created_at DESC);

CREATE TABLE IF NOT EXISTS qa_request_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  request_id TEXT NOT NULL REFERENCES qa_requests(id) ON DELETE CASCADE,
  version INTEGER NOT NULL CHECK(version > 0),
  actor_role TEXT NOT NULL CHECK(actor_role IN ('passenger','driver')),
  from_state TEXT,
  to_state TEXT NOT NULL,
  created_at TEXT NOT NULL,
  UNIQUE(request_id, version)
);

-- One INSERT/UPDATE statement is one atomic state+event commit.
-- The event is part of the same SQLite statement, so a failed write
-- cannot leave a request without its corresponding history row.
CREATE TRIGGER IF NOT EXISTS qa_request_created AFTER INSERT ON qa_requests
BEGIN
  INSERT INTO qa_request_events(request_id,version,actor_role,from_state,to_state,created_at)
  VALUES (NEW.id, NEW.version, NEW.last_actor_role, NULL, NEW.state, NEW.created_at);
END;

CREATE TRIGGER IF NOT EXISTS qa_request_transitioned AFTER UPDATE OF state ON qa_requests
WHEN NEW.state <> OLD.state
BEGIN
  INSERT INTO qa_request_events(request_id,version,actor_role,from_state,to_state,created_at)
  VALUES (NEW.id, NEW.version, NEW.last_actor_role, OLD.state, NEW.state, NEW.updated_at);
END;

-- Quick Trip Live QA; isolated D1 only. Never apply to production.
CREATE TABLE IF NOT EXISTS qa_tracking_consent (
  request_id TEXT PRIMARY KEY REFERENCES qa_requests(id) ON DELETE CASCADE,
  driver_ref TEXT NOT NULL,
  enabled INTEGER NOT NULL DEFAULT 0 CHECK(enabled IN (0,1)),
  updated_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS qa_tracking_position (
  request_id TEXT PRIMARY KEY REFERENCES qa_requests(id) ON DELETE CASCADE,
  seq INTEGER NOT NULL CHECK(seq>0),
  lat REAL NOT NULL CHECK(lat BETWEEN -35.5 AND -30),
  lng REAL NOT NULL CHECK(lng BETWEEN -59.5 AND -52),
  accuracy_m REAL NOT NULL CHECK(accuracy_m>0 AND accuracy_m<=50),
  heading_deg REAL CHECK(heading_deg>=0 AND heading_deg<360),
  measured_at TEXT NOT NULL,
  received_at TEXT NOT NULL,
  expires_at TEXT NOT NULL
);
-- Terminal state clears GPS and revokes consent within the transition transaction.
CREATE TRIGGER IF NOT EXISTS qa_tracking_terminal AFTER UPDATE OF state ON qa_requests
WHEN NEW.state IN ('RECHAZADA','CANCELADA','FINALIZADA')
BEGIN
  DELETE FROM qa_tracking_position WHERE request_id=NEW.id;
  UPDATE qa_tracking_consent SET enabled=0,updated_at=NEW.updated_at WHERE request_id=NEW.id;
END;
