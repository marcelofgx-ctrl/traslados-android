-- SIMULATED RPCS ONLY. NEVER EXECUTE IN SUPABASE REAL.
-- Guard: PG17 disposable qt_capacity_test with synthetic data; no real auth or ETA.
DO $$
BEGIN
  IF current_database() <> 'qt_capacity_test'
     OR current_setting('server_version_num')::int < 170000
     OR current_setting('server_version_num')::int >= 180000
  THEN RAISE EXCEPTION 'Disposable PostgreSQL 17 qt_capacity_test required'; END IF;
END $$;

-- Extend the existing qa_capacity.fake_reservations, never public.reservations.
ALTER TABLE qa_capacity.fake_reservations
  ADD COLUMN quote_status text NOT NULL DEFAULT 'PRECIO_FIJO',
  ADD COLUMN confirmed_at timestamptz;

-- SIMULATION of driver_decide_fixed_pickup_v1:
-- advisory lock + row lock + validated pending price + committed update.
CREATE FUNCTION qa_capacity.driver_decide_fixed_pickup_replica(
  p_id uuid,p_accept boolean) RETURNS text
LANGUAGE plpgsql SECURITY INVOKER SET search_path TO qa_capacity,pg_temp AS $$
DECLARE r qa_capacity.fake_reservations%rowtype;
BEGIN
  IF p_accept THEN
    PERFORM pg_advisory_xact_lock(hashtextextended('traslados_availability',0));
  END IF;
  SELECT * INTO r FROM qa_capacity.fake_reservations WHERE id=p_id FOR UPDATE;
  IF NOT FOUND OR r.fare_mode <> 'fixed' THEN RAISE EXCEPTION 'not_fixed'; END IF;
  IF r.status='ACEPTADA' AND p_accept THEN RETURN 'already_decided'; END IF;
  IF r.status <> 'PENDIENTE' OR r.quote_status <> 'PRECIO_FIJO'
  THEN RAISE EXCEPTION 'not_pending_fixed'; END IF;
  UPDATE qa_capacity.fake_reservations
  SET status=CASE WHEN p_accept THEN 'ACEPTADA' ELSE 'RECHAZADA' END,
      quote_status=CASE WHEN p_accept THEN 'ACEPTADO' ELSE 'RECHAZADO' END,
      confirmed_at=CASE WHEN p_accept THEN now() ELSE NULL END
  WHERE id=p_id;
  RETURN CASE WHEN p_accept THEN 'accepted' ELSE 'rejected' END;
END $$;

-- SIMULATION of customer_quote_decision_v13: advisory + SELECT FOR UPDATE.
-- Deliberately omits production session/token, route/ETA and expiry calculations;
-- the test asserts capacity only, not complete RPC semantic equivalence.
CREATE FUNCTION qa_capacity.customer_quote_decision_v13_replica(
  p_id uuid,p_accept boolean) RETURNS text
LANGUAGE plpgsql SECURITY INVOKER SET search_path TO qa_capacity,pg_temp AS $$
DECLARE r qa_capacity.fake_reservations%rowtype;
BEGIN
  IF p_accept THEN
    PERFORM pg_advisory_xact_lock(hashtextextended('traslados_availability',0));
  END IF;
  SELECT * INTO r FROM qa_capacity.fake_reservations WHERE id=p_id FOR UPDATE;
  IF NOT FOUND OR r.fare_mode <> 'quote'
     OR r.status <> 'PENDIENTE' OR r.quote_status <> 'ENVIADO'
  THEN RAISE EXCEPTION 'no_pending_quote'; END IF;
  UPDATE qa_capacity.fake_reservations SET
    status=CASE WHEN p_accept THEN 'ACEPTADA' ELSE 'RECHAZADA' END,
    quote_status=CASE WHEN p_accept THEN 'ACEPTADO' ELSE 'RECHAZADO' END,
    confirmed_at=CASE WHEN p_accept THEN now() ELSE NULL END
  WHERE id=p_id;
  RETURN CASE WHEN p_accept THEN 'accepted' ELSE 'rejected' END;
END $$;

-- SIMULATION of legacy v10: UPDATE without advisory lock or availability call.
CREATE FUNCTION qa_capacity.customer_quote_decision_v10_replica(
  p_id uuid,p_accept boolean) RETURNS text
LANGUAGE plpgsql SECURITY INVOKER SET search_path TO qa_capacity,pg_temp AS $$
BEGIN
  UPDATE qa_capacity.fake_reservations SET
    status=CASE WHEN p_accept THEN 'ACEPTADA' ELSE 'RECHAZADA' END,
    quote_status=CASE WHEN p_accept THEN 'ACEPTADO' ELSE 'RECHAZADO' END,
    confirmed_at=CASE WHEN p_accept THEN now() ELSE NULL END
  WHERE id=p_id AND fare_mode='quote'
    AND status='PENDIENTE' AND quote_status='ENVIADO';
  IF NOT FOUND THEN RAISE EXCEPTION 'no_pending_legacy_quote'; END IF;
  RETURN CASE WHEN p_accept THEN 'accepted' ELSE 'rejected' END;
END $$;

-- SIMULATION of driver_set_status_v2: FOR UPDATE + allowed transitions.
CREATE FUNCTION qa_capacity.driver_set_status_v2_replica(
  p_id uuid,p_target text) RETURNS text
LANGUAGE plpgsql SECURITY INVOKER SET search_path TO qa_capacity,pg_temp AS $$
DECLARE r qa_capacity.fake_reservations%rowtype;
BEGIN
  SELECT * INTO r FROM qa_capacity.fake_reservations WHERE id=p_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'missing_reservation'; END IF;
  IF r.status=p_target THEN RETURN 'unchanged'; END IF;
  IF NOT (
     (r.status='PENDIENTE' AND p_target IN ('ACEPTADA','RECHAZADA','CANCELADA'))
     OR (r.status='ACEPTADA' AND p_target IN ('EN_VIAJE','CANCELADA'))
     OR (r.status='EN_VIAJE' AND p_target='FINALIZADA')
  ) THEN RAISE EXCEPTION 'transition_not_allowed'; END IF;
  UPDATE qa_capacity.fake_reservations SET status=p_target WHERE id=p_id;
  RETURN p_target;
END $$;

-- SIMULATION of driver_set_pickup_phase_v1: schedules acquire the index
-- as soon as pickup_phase changes to EN_CAMINO.
CREATE FUNCTION qa_capacity.driver_set_pickup_phase_v1_replica(
  p_id uuid,p_phase text) RETURNS text
LANGUAGE plpgsql SECURITY INVOKER SET search_path TO qa_capacity,pg_temp AS $$
DECLARE r qa_capacity.fake_reservations%rowtype;
BEGIN
  SELECT * INTO r FROM qa_capacity.fake_reservations WHERE id=p_id FOR UPDATE;
  IF NOT FOUND OR r.status <> 'ACEPTADA'
     OR p_phase NOT IN ('EN_CAMINO','EN_PUNTO')
  THEN RAISE EXCEPTION 'invalid_pickup'; END IF;
  IF r.pickup_phase=p_phase THEN RETURN 'unchanged'; END IF;
  IF p_phase='EN_PUNTO' AND r.pickup_phase <> 'EN_CAMINO'
  THEN RAISE EXCEPTION 'must_be_on_way'; END IF;
  IF r.pickup_phase='EN_PUNTO' THEN RAISE EXCEPTION 'already_arrived'; END IF;
  UPDATE qa_capacity.fake_reservations SET pickup_phase=p_phase WHERE id=p_id;
  RETURN p_phase;
END $$;
