-- PROPOSAL ONLY; DO NOT APPLY TO THE CONNECTED SUPABASE DATABASE.
-- PostgreSQL uniqueness is transaction-safe even against simultaneous RPCs.
-- Global one-driver slot: not suitable for a future multi-driver deployment.
-- A future scheduled trip may be accepted, but blocks the slot once the
-- driver begins going to pickup (EN_CAMINO) or starts EN_VIAJE.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

DO $guard$
DECLARE operational_count integer;
BEGIN
  SELECT count(*) INTO operational_count
  FROM public.reservations
  WHERE status='EN_VIAJE'
     OR (status='ACEPTADA'
         AND (pickup_mode='now' OR pickup_phase IN ('EN_CAMINO','EN_PUNTO')));
  IF operational_count > 1 THEN
    RAISE EXCEPTION
      'QT_PRECHECK_MULTIPLE_ACTIVE: % unclosed services; review manually before enabling guard',
      operational_count;
  END IF;
END
$guard$;

CREATE UNIQUE INDEX quicktrip_one_operational_service_v1_uq
  ON public.reservations ((1))
  WHERE status='EN_VIAJE'
     OR (status='ACEPTADA'
         AND (pickup_mode='now' OR pickup_phase IN ('EN_CAMINO','EN_PUNTO')));
COMMIT;