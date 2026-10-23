-- DRAFT ONLY — NEVER execute on live Supabase without separate approval.
-- A database trigger covers ALL RPC/REST acceptance paths, not just one function.
-- It serializes acceptance for the single Quick Trip driver via a transaction advisory lock.
-- Existing open reservations are never silently closed or modified.
CREATE OR REPLACE FUNCTION public.qt_guard_one_immediate_open_v1()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER
SET search_path TO 'public','pg_temp' AS $$
DECLARE
  v_open boolean;
BEGIN
  -- Do not restrict programmed/budget reservations; their schedule logic remains intact.
  -- Confirm actual enum and fare_mode values before executing migration.
  IF NEW.is_demo IS DISTINCT FROM false
     OR NEW.fare_mode IS DISTINCT FROM 'fixed'
     OR NEW.status::text NOT IN ('ACEPTADA','ACEPTADA_CLIENTE','CONFIRMADA','EN_VIAJE')
  THEN RETURN NEW; END IF;

  -- Only guard the transition into an open immediate service, not unrelated edits
  -- to an already accepted row (e.g. pickup phase or timestamps).
  IF TG_OP='UPDATE' AND OLD.status::text IN
      ('ACEPTADA','ACEPTADA_CLIENTE','CONFIRMADA','EN_VIAJE') THEN
    RETURN NEW;
  END IF;

  -- Same lock key as driver_decide_fixed_pickup_v1 and customer_quote_decision_v13.
  -- Concurrent attempts serialize; second sees committed first reservation.
  PERFORM pg_advisory_xact_lock(hashtextextended('traslados_availability',0));

  SELECT EXISTS(
    SELECT 1 FROM public.reservations r
    WHERE r.id IS DISTINCT FROM NEW.id
      AND coalesce(r.is_demo,false)=false
      AND r.fare_mode='fixed'
      AND r.status::text IN ('ACEPTADA','ACEPTADA_CLIENTE','CONFIRMADA','EN_VIAJE')
  ) INTO v_open;
  IF v_open THEN
    RAISE EXCEPTION 'QT_CONDUCTOR_OCUPADO'
      USING ERRCODE='P0001', DETAIL='ONE_IMMEDIATE_SERVICE_OPEN';
  END IF;
  RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS qt_guard_one_immediate_open_v1 ON public.reservations;
CREATE TRIGGER qt_guard_one_immediate_open_v1
BEFORE INSERT OR UPDATE OF status ON public.reservations
FOR EACH ROW EXECUTE FUNCTION public.qt_guard_one_immediate_open_v1();

-- IMPORTANT: audit actual pickup_mode field semantics. 'fixed' is fare_mode,
-- not necessarily 'immediate'; if scheduled trips can use fixed fares, use
-- the canonical immediate discriminator instead BEFORE this is deployed.
-- Verify all acceptance paths and deadlock lock order before production.
