-- PRIVATE, SYNTHETIC QA SCHEMA for disposable qt_capacity_test PostgreSQL.
-- Mirrors relevant behavior, DOES NOT create or modify production RPCs.
DO $$ BEGIN
 IF current_database() <> 'qt_capacity_test' THEN
  RAISE EXCEPTION 'Disposable GPS QA database required';
 END IF;
END $$;
CREATE SCHEMA qa_gps;
CREATE TABLE qa_gps.reservations(
 id uuid PRIMARY KEY,
 public_token text NOT NULL,
 status text NOT NULL DEFAULT 'PENDIENTE',
 pickup_phase text
);
CREATE TABLE qa_gps.presence(
 singleton_id integer PRIMARY KEY,
 enabled boolean NOT NULL,
 shift_active boolean NOT NULL,
 shift_paused boolean NOT NULL,
 device_id uuid NOT NULL,
 device_secret text NOT NULL
);
CREATE TABLE qa_gps.position(
 reservation_id uuid PRIMARY KEY REFERENCES qa_gps.reservations(id),
 device_id uuid NOT NULL,
 lat double precision NOT NULL,
 lng double precision NOT NULL,
 accuracy_m real NOT NULL,
 gps_recorded_at timestamptz NOT NULL,
 received_at timestamptz NOT NULL
);
-- Guarded private publisher for exactly one authorized active booking.
-- Unlike public driver-availability, live GPS for an authenticated passenger
-- should also work DURING EN_VIAJE, never exposing public pickup coordinates.
CREATE FUNCTION qa_gps.publish_fake(
 p_device_id uuid,p_secret text,p_lat double precision,p_lng double precision,
 p_accuracy real,p_gps timestamptz
) RETURNS jsonb LANGUAGE plpgsql SECURITY INVOKER AS $$
DECLARE v_id uuid; n integer;
BEGIN
 IF NOT EXISTS (
  SELECT 1 FROM qa_gps.presence p WHERE p.singleton_id=1
    AND p.enabled AND p.shift_active AND NOT p.shift_paused
    AND p.device_id=p_device_id AND p.device_secret=p_secret
 ) THEN RETURN jsonb_build_object('ok',false,'reason','unauthorized_or_disabled'); END IF;
 IF p_lat NOT BETWEEN -35.3 AND -30 OR p_lng NOT BETWEEN -58.8 AND -52.9
   OR p_accuracy NOT BETWEEN 0 AND 45 OR p_gps IS NULL
 THEN RETURN jsonb_build_object('ok',false,'reason','gps_unavailable'); END IF;
 IF p_gps < now()-interval '75 seconds' OR p_gps > now()+interval '10 seconds'
 THEN RETURN jsonb_build_object('ok',false,'reason','stale_gps'); END IF;
 SELECT count(*),min(id::text)::uuid INTO n,v_id FROM (
   SELECT id FROM qa_gps.reservations
   WHERE status='EN_VIAJE' OR
     (status IN ('ACEPTADA','CONFIRMADA','ACEPTADA_CLIENTE')
       AND pickup_phase IN ('EN_CAMINO','EN_PUNTO')) LIMIT 2
 ) AS live;
 IF n<>1 THEN RETURN jsonb_build_object('ok',false,'reason','no_unique_active_reservation'); END IF;
 INSERT INTO qa_gps.position
   (reservation_id,device_id,lat,lng,accuracy_m,gps_recorded_at,received_at)
 VALUES(v_id,p_device_id,p_lat,p_lng,p_accuracy,p_gps,now())
 ON CONFLICT(reservation_id) DO UPDATE SET
   device_id=excluded.device_id, lat=excluded.lat, lng=excluded.lng,
   accuracy_m=excluded.accuracy_m,
   gps_recorded_at=excluded.gps_recorded_at,
   received_at=excluded.received_at
 WHERE qa_gps.position.gps_recorded_at<=excluded.gps_recorded_at;
 RETURN jsonb_build_object('ok',true,'updated',true);
END $$;

-- Proposed fail-closed passenger-read contract. In production, use the
-- actual authenticated customer session OR a >=48-char public reservation token.
-- This simulation also rejects multiple active services at READ TIME, avoiding
-- up to 75 seconds of a previously published position during an anomaly.
CREATE FUNCTION qa_gps.customer_position_fake(
 p_reservation_id uuid,p_public_token text
) RETURNS jsonb LANGUAGE plpgsql SECURITY INVOKER AS $$
DECLARE r qa_gps.reservations%rowtype; v qa_gps.position%rowtype;
        presence qa_gps.presence%rowtype; phase text; n integer;
BEGIN
 IF p_reservation_id IS NULL OR length(coalesce(p_public_token,''))<48 THEN
   RETURN jsonb_build_object('available',false,'reason','unauthorized'); END IF;
 SELECT * INTO r FROM qa_gps.reservations
 WHERE id=p_reservation_id AND public_token=p_public_token;
 IF NOT FOUND THEN RETURN jsonb_build_object('available',false,'reason','unauthorized'); END IF;
 IF r.status NOT IN ('ACEPTADA','ACEPTADA_CLIENTE','CONFIRMADA','EN_VIAJE') THEN
   RETURN jsonb_build_object('available',false,'reason','not_active'); END IF;
 phase:=CASE WHEN r.status='EN_VIAJE' THEN 'EN_VIAJE'
             ELSE coalesce(r.pickup_phase,'SIN_INICIAR') END;
 IF phase NOT IN ('EN_CAMINO','EN_PUNTO','EN_VIAJE') THEN
   RETURN jsonb_build_object('available',false,'reason','preparing_pickup','phase',phase); END IF;
 SELECT count(*) INTO n FROM (
   SELECT id FROM qa_gps.reservations
   WHERE status='EN_VIAJE' OR
     (status IN ('ACEPTADA','ACEPTADA_CLIENTE','CONFIRMADA')
       AND pickup_phase IN ('EN_CAMINO','EN_PUNTO')) LIMIT 2
 ) live;
 IF n<>1 THEN RETURN jsonb_build_object(
   'available',false,'reason','ambiguous_driver_service','phase',phase); END IF;
 SELECT * INTO v FROM qa_gps.position WHERE reservation_id=p_reservation_id;
 SELECT * INTO presence FROM qa_gps.presence WHERE singleton_id=1;
 IF v.reservation_id IS NULL OR presence.singleton_id IS NULL
   OR NOT presence.enabled OR NOT presence.shift_active OR presence.shift_paused
   OR presence.device_id IS DISTINCT FROM v.device_id
   OR v.received_at < now()-interval '75 seconds'
   OR v.gps_recorded_at < now()-interval '75 seconds'
   OR v.gps_recorded_at > now()+interval '10 seconds'
   OR v.accuracy_m >45 THEN
  RETURN jsonb_build_object('available',false,'reason','position_unavailable','phase',phase);
 END IF;
 RETURN jsonb_build_object(
   'available',true,'phase',phase,
   'driverLat',v.lat,'driverLng',v.lng,
   'accuracyM',v.accuracy_m,
   'gpsAgeSec',greatest(0,floor(extract(epoch from now()-v.gps_recorded_at))::integer));
END $$;
