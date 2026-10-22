-- QUICK TRIP 4B: synthetic PostgreSQL laboratory ONLY.
-- This deliberately does NOT implement JWT cryptographic verification.
-- request.jwt.claim.sub is injected by the isolated test harness. Production
-- must rely on Supabase Auth/PostgREST verifying the token BEFORE setting claims.
BEGIN;
CREATE ROLE lab_anon NOLOGIN;
CREATE ROLE lab_authenticated NOLOGIN;
CREATE SCHEMA auth;
CREATE SCHEMA lab_data;
CREATE SCHEMA lab_internal;
CREATE SCHEMA lab_api;
REVOKE ALL ON SCHEMA lab_data, lab_internal, lab_api FROM PUBLIC;
GRANT USAGE ON SCHEMA lab_api, lab_internal, lab_data TO lab_authenticated;
GRANT USAGE ON SCHEMA lab_api TO lab_anon;
GRANT USAGE ON SCHEMA auth TO lab_authenticated, lab_anon;

CREATE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql STABLE SECURITY INVOKER
AS $f$ SELECT NULLIF(current_setting('request.jwt.claim.sub', true), '')::uuid $f$;
REVOKE ALL ON FUNCTION auth.uid() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION auth.uid() TO lab_authenticated, lab_anon;

CREATE TABLE lab_data.drivers (
  id uuid PRIMARY KEY,
  active boolean NOT NULL
);
CREATE TABLE lab_data.reservations (
  id uuid PRIMARY KEY,
  driver_id uuid REFERENCES lab_data.drivers(id),
  status text NOT NULL CHECK (status IN ('PENDIENTE','ACEPTADA','RECHAZADA','EN_VIAJE','FINALIZADA','CANCELADA')),
  quote_status text NOT NULL CHECK (quote_status IN ('PRECIO_FIJO','ACEPTADO','RECHAZADO','ENVIADO')),
  fare_mode text NOT NULL CHECK (fare_mode IN ('fixed','quote')),
  pickup_mode text NOT NULL,
  quote_final_total numeric(12,2) NOT NULL,
  route_distance_km numeric(10,2) NOT NULL,
  route_duration_min integer NOT NULL DEFAULT 20,
  created_at timestamptz NOT NULL,
  available boolean NOT NULL DEFAULT true,
  confirmed_at timestamptz,
  quote_accepted_at timestamptz,
  quote_rejected_at timestamptz
);
CREATE TABLE lab_data.decision_requests (
  driver_id uuid NOT NULL,
  request_id uuid NOT NULL,
  reservation_id uuid NOT NULL,
  accept boolean NOT NULL,
  response jsonb NOT NULL,
  PRIMARY KEY (driver_id, request_id)
);
CREATE TABLE lab_data.reservation_events (
  reservation_id uuid NOT NULL,
  actor uuid NOT NULL,
  state text NOT NULL,
  at timestamptz NOT NULL DEFAULT now()
);

CREATE FUNCTION lab_internal.fixed_price_guard() RETURNS trigger
LANGUAGE plpgsql SECURITY INVOKER SET search_path = pg_catalog AS $f$
BEGIN
  IF OLD.fare_mode='fixed' THEN
    IF NEW.quote_final_total IS DISTINCT FROM OLD.quote_final_total
       OR NEW.route_distance_km IS DISTINCT FROM OLD.route_distance_km
       OR NEW.fare_mode IS DISTINCT FROM OLD.fare_mode THEN
      RAISE EXCEPTION 'FIXED_FARE_IMMUTABLE';
    END IF;
    IF OLD.status='PENDIENTE' AND NEW.status='ACEPTADA'
      AND (NEW.quote_status <> 'ACEPTADO' OR NEW.confirmed_at IS NULL) THEN
      RAISE EXCEPTION 'FIXED_FARE_ACCEPT_INCOMPLETE';
    END IF;
    IF OLD.status='PENDIENTE' AND NEW.status='RECHAZADA'
      AND NEW.quote_status <> 'RECHAZADO' THEN
      RAISE EXCEPTION 'FIXED_FARE_REJECT_INCOMPLETE';
    END IF;
  END IF;
  RETURN NEW;
END $f$;
CREATE TRIGGER lab_fixed_fare_guard BEFORE UPDATE ON lab_data.reservations
FOR EACH ROW EXECUTE FUNCTION lab_internal.fixed_price_guard();

ALTER TABLE lab_data.drivers ENABLE ROW LEVEL SECURITY;
ALTER TABLE lab_data.reservations ENABLE ROW LEVEL SECURITY;
ALTER TABLE lab_data.decision_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE lab_data.reservation_events ENABLE ROW LEVEL SECURITY;

CREATE POLICY lab_driver_self ON lab_data.drivers FOR SELECT TO lab_authenticated
USING (id = auth.uid() AND active);
CREATE POLICY lab_direct_assigned ON lab_data.reservations FOR SELECT TO lab_authenticated
USING (driver_id = auth.uid() AND EXISTS
  (SELECT 1 FROM lab_data.drivers d WHERE d.id=auth.uid() AND d.active));
GRANT SELECT ON lab_data.drivers, lab_data.reservations TO lab_authenticated;
-- NO direct UPDATE/INSERT/DELETE/TRUNCATE grants to authenticated or anon.
REVOKE ALL ON ALL TABLES IN SCHEMA lab_data FROM PUBLIC, lab_anon;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON ALL TABLES IN SCHEMA lab_data FROM lab_authenticated;

CREATE FUNCTION lab_api.driver_session_status_auth_v1() RETURNS jsonb
LANGUAGE sql STABLE SECURITY INVOKER SET search_path = pg_catalog AS $f$
SELECT jsonb_build_object('authorized', EXISTS (
    SELECT 1 FROM lab_data.drivers d WHERE d.id=auth.uid() AND d.active
), 'driver_id', CASE WHEN EXISTS(
    SELECT 1 FROM lab_data.drivers d WHERE d.id=auth.uid() AND d.active
) THEN auth.uid() ELSE NULL END)
$f$;

CREATE FUNCTION lab_api.driver_list_direct_auth_v1() RETURNS jsonb
LANGUAGE sql STABLE SECURITY INVOKER SET search_path = pg_catalog AS $f$
SELECT coalesce(jsonb_agg(jsonb_build_object(
  'id',r.id, 'driver_id',r.driver_id, 'status',r.status,
  'pickup_mode',r.pickup_mode, 'fare_mode',r.fare_mode,
  'quote_status',r.quote_status, 'quote_final_total',r.quote_final_total,
  'route_distance_km',r.route_distance_km, 'route_duration_min',r.route_duration_min
) ORDER BY r.created_at DESC), '[]'::jsonb)
FROM lab_data.reservations r
WHERE r.status='PENDIENTE' AND r.fare_mode='fixed'
AND r.quote_status='PRECIO_FIJO' AND r.quote_final_total>0
AND r.route_distance_km>0 AND
(r.pickup_mode='now' OR (r.pickup_mode ~ '^([1-9]|[1-9][0-9]|1[01][0-9]|120)$'))
AND r.created_at >= now()-interval '90 minutes'
$f$;

-- The transaction-critical function is in a private, non-API schema.
-- It remains SECURITY DEFINER but performs explicit actor + ownership checks.
CREATE FUNCTION lab_internal.decide_direct_impl_v1(
  p_reservation_id uuid, p_accept boolean, p_request_id uuid
) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog AS $f$
DECLARE
  actor uuid := auth.uid();
  trip lab_data.reservations%ROWTYPE;
  prev lab_data.decision_requests%ROWTYPE;
  response jsonb;
BEGIN
  IF actor IS NULL OR NOT EXISTS
    (SELECT 1 FROM lab_data.drivers WHERE id=actor AND active) THEN
    RAISE EXCEPTION 'NOT_AUTHORIZED';
  END IF;
  IF p_reservation_id IS NULL OR p_accept IS NULL OR p_request_id IS NULL THEN
    RAISE EXCEPTION 'INVALID_ARGUMENT';
  END IF;
  -- Deterministic exclusive lock by reservation ID for concurrency + retries.
  PERFORM pg_advisory_xact_lock(hashtextextended('quicktrip-4b:'||p_reservation_id::text,0));
  SELECT * INTO prev FROM lab_data.decision_requests
  WHERE driver_id=actor AND request_id=p_request_id;
  IF FOUND THEN
    IF prev.reservation_id<>p_reservation_id OR prev.accept<>p_accept THEN
      RAISE EXCEPTION 'IDEMPOTENCY_CONFLICT';
    END IF;
    RETURN prev.response;
  END IF;
  SELECT * INTO trip FROM lab_data.reservations
    WHERE id=p_reservation_id FOR UPDATE;
  IF NOT FOUND OR trip.driver_id IS DISTINCT FROM actor THEN
    RAISE EXCEPTION 'NOT_AUTHORIZED';
  END IF;
  -- Status must be checked before quote_status: a previously accepted
  -- fixed-price trip legitimately has quote_status='ACEPTADO'.
  IF trip.status<>'PENDIENTE' THEN
    RAISE EXCEPTION 'ALREADY_DECIDED';
  END IF;
  IF trip.fare_mode<>'fixed' OR trip.quote_status<>'PRECIO_FIJO'
       OR NOT (trip.pickup_mode='now' OR
          trip.pickup_mode ~ '^([1-9]|[1-9][0-9]|1[01][0-9]|120)
  IF trip.created_at < now()-interval '90 minutes' THEN
    RAISE EXCEPTION 'EXPIRED';
  END IF;
  -- The local boolean simulates existing availability_route_check_v11_4;
  -- it is NOT a substitute for the real engine in production.
  IF p_accept AND NOT trip.available THEN
    RAISE EXCEPTION 'NOT_AVAILABLE';
  END IF;
  IF p_accept THEN
    UPDATE lab_data.reservations SET status='ACEPTADA',
      quote_status='ACEPTADO', confirmed_at=now(),quote_accepted_at=now()
      WHERE id=trip.id;
  ELSE
    UPDATE lab_data.reservations SET status='RECHAZADA',
      quote_status='RECHAZADO',quote_rejected_at=now()
      WHERE id=trip.id;
  END IF;
  INSERT INTO lab_data.reservation_events(reservation_id,actor,state)
    VALUES(trip.id, actor, CASE WHEN p_accept THEN 'ACEPTADA' ELSE 'RECHAZADA' END);
  response:=jsonb_build_object('id',trip.id,'status',
    CASE WHEN p_accept THEN 'ACEPTADA' ELSE 'RECHAZADA' END,
    'already_decided',false, 'request_id',p_request_id);
  INSERT INTO lab_data.decision_requests(driver_id,request_id,reservation_id,accept,response)
    VALUES(actor,p_request_id,p_reservation_id,p_accept,response);
  RETURN response;
END $f$;

CREATE FUNCTION lab_api.driver_decide_direct_auth_v1(
  p_reservation_id uuid, p_accept boolean, p_request_id uuid
) RETURNS jsonb LANGUAGE sql VOLATILE SECURITY INVOKER
SET search_path = pg_catalog AS $f$
SELECT lab_internal.decide_direct_impl_v1(p_reservation_id,p_accept,p_request_id)
$f$;

REVOKE ALL ON ALL FUNCTIONS IN SCHEMA lab_api, lab_internal FROM PUBLIC, lab_anon;
GRANT EXECUTE ON FUNCTION lab_api.driver_session_status_auth_v1() TO lab_authenticated;
GRANT EXECUTE ON FUNCTION lab_api.driver_list_direct_auth_v1() TO lab_authenticated;
GRANT EXECUTE ON FUNCTION lab_api.driver_decide_direct_auth_v1(uuid,boolean,uuid) TO lab_authenticated;
GRANT EXECUTE ON FUNCTION lab_internal.decide_direct_impl_v1(uuid,boolean,uuid) TO lab_authenticated;

INSERT INTO lab_data.drivers(id,active) VALUES
 ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',true),
 ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',true),
 ('cccccccc-cccc-cccc-cccc-cccccccccccc',false);

INSERT INTO lab_data.reservations
(id,driver_id,status,quote_status,fare_mode,pickup_mode,
 quote_final_total,route_distance_km,route_duration_min,created_at,available)
VALUES
 ('00000000-0000-0000-0000-000000000001','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  'PENDIENTE','PRECIO_FIJO','fixed','now',840,21,30,now()-interval '2 minutes',true),
 ('00000000-0000-0000-0000-000000000002','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  'PENDIENTE','PRECIO_FIJO','fixed','15',450,10,20,now()-interval '3 minutes',true),
 ('00000000-0000-0000-0000-000000000003','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  'PENDIENTE','ENVIADO','quote','schedule',1200,30,35,now()-interval '3 minutes',true),
 ('00000000-0000-0000-0000-000000000004','bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
  'PENDIENTE','PRECIO_FIJO','fixed','now',500,13,20,now()-interval '4 minutes',true),
 ('00000000-0000-0000-0000-000000000005','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  'PENDIENTE','PRECIO_FIJO','fixed','now',550,14,22,now()-interval '95 minutes',true),
 ('00000000-0000-0000-0000-000000000006','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  'PENDIENTE','PRECIO_FIJO','fixed','now',250,4,8,now()-interval '4 minutes',false),
 ('00000000-0000-0000-0000-000000000007','cccccccc-cccc-cccc-cccc-cccccccccccc',
  'PENDIENTE','PRECIO_FIJO','fixed','now',320,7,12,now()-interval '2 minutes',true),
 ('00000000-0000-0000-0000-000000000008','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  'CANCELADA','PRECIO_FIJO','fixed','now',700,18,26,now()-interval '4 minutes',true),
 ('00000000-0000-0000-0000-000000000009','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  'PENDIENTE','PRECIO_FIJO','fixed','now',0,5,8,now()-interval '4 minutes',true);
COMMIT;
)
       OR trip.quote_final_total<=0 OR trip.route_distance_km<=0 THEN
    RAISE EXCEPTION 'NOT_DIRECT_FIXED';
  END IF;
  IF trip.created_at < now()-interval '90 minutes' THEN
    RAISE EXCEPTION 'EXPIRED';
  END IF;
  -- The local boolean simulates existing availability_route_check_v11_4;
  -- it is NOT a substitute for the real engine in production.
  IF p_accept AND NOT trip.available THEN
    RAISE EXCEPTION 'NOT_AVAILABLE';
  END IF;
  IF p_accept THEN
    UPDATE lab_data.reservations SET status='ACEPTADA',
      quote_status='ACEPTADO', confirmed_at=now(),quote_accepted_at=now()
      WHERE id=trip.id;
  ELSE
    UPDATE lab_data.reservations SET status='RECHAZADA',
      quote_status='RECHAZADO',quote_rejected_at=now()
      WHERE id=trip.id;
  END IF;
  INSERT INTO lab_data.reservation_events(reservation_id,actor,state)
    VALUES(trip.id, actor, CASE WHEN p_accept THEN 'ACEPTADA' ELSE 'RECHAZADA' END);
  response:=jsonb_build_object('id',trip.id,'status',
    CASE WHEN p_accept THEN 'ACEPTADA' ELSE 'RECHAZADA' END,
    'already_decided',false, 'request_id',p_request_id);
  INSERT INTO lab_data.decision_requests(driver_id,request_id,reservation_id,accept,response)
    VALUES(actor,p_request_id,p_reservation_id,p_accept,response);
  RETURN response;
END $f$;

CREATE FUNCTION lab_api.driver_decide_direct_auth_v1(
  p_reservation_id uuid, p_accept boolean, p_request_id uuid
) RETURNS jsonb LANGUAGE sql VOLATILE SECURITY INVOKER
SET search_path = pg_catalog AS $f$
SELECT lab_internal.decide_direct_impl_v1(p_reservation_id,p_accept,p_request_id)
$f$;

REVOKE ALL ON ALL FUNCTIONS IN SCHEMA lab_api, lab_internal FROM PUBLIC, lab_anon;
GRANT EXECUTE ON FUNCTION lab_api.driver_session_status_auth_v1() TO lab_authenticated;
GRANT EXECUTE ON FUNCTION lab_api.driver_list_direct_auth_v1() TO lab_authenticated;
GRANT EXECUTE ON FUNCTION lab_api.driver_decide_direct_auth_v1(uuid,boolean,uuid) TO lab_authenticated;
GRANT EXECUTE ON FUNCTION lab_internal.decide_direct_impl_v1(uuid,boolean,uuid) TO lab_authenticated;

INSERT INTO lab_data.drivers(id,active) VALUES
 ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',true),
 ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',true),
 ('cccccccc-cccc-cccc-cccc-cccccccccccc',false);

INSERT INTO lab_data.reservations
(id,driver_id,status,quote_status,fare_mode,pickup_mode,
 quote_final_total,route_distance_km,route_duration_min,created_at,available)
VALUES
 ('00000000-0000-0000-0000-000000000001','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  'PENDIENTE','PRECIO_FIJO','fixed','now',840,21,30,now()-interval '2 minutes',true),
 ('00000000-0000-0000-0000-000000000002','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  'PENDIENTE','PRECIO_FIJO','fixed','15',450,10,20,now()-interval '3 minutes',true),
 ('00000000-0000-0000-0000-000000000003','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  'PENDIENTE','ENVIADO','quote','schedule',1200,30,35,now()-interval '3 minutes',true),
 ('00000000-0000-0000-0000-000000000004','bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
  'PENDIENTE','PRECIO_FIJO','fixed','now',500,13,20,now()-interval '4 minutes',true),
 ('00000000-0000-0000-0000-000000000005','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  'PENDIENTE','PRECIO_FIJO','fixed','now',550,14,22,now()-interval '95 minutes',true),
 ('00000000-0000-0000-0000-000000000006','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  'PENDIENTE','PRECIO_FIJO','fixed','now',250,4,8,now()-interval '4 minutes',false),
 ('00000000-0000-0000-0000-000000000007','cccccccc-cccc-cccc-cccc-cccccccccccc',
  'PENDIENTE','PRECIO_FIJO','fixed','now',320,7,12,now()-interval '2 minutes',true),
 ('00000000-0000-0000-0000-000000000008','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  'CANCELADA','PRECIO_FIJO','fixed','now',700,18,26,now()-interval '4 minutes',true),
 ('00000000-0000-0000-0000-000000000009','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  'PENDIENTE','PRECIO_FIJO','fixed','now',0,5,8,now()-interval '4 minutes',true);
COMMIT;
