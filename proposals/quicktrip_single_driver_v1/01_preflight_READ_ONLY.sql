-- PROPOSAL. Read-only preflight; never close or alter reservations here.
-- All active immediate pickups count, even after the scheduled ETA.
SELECT count(*) AS operational_services
FROM public.reservations
WHERE status='EN_VIAJE'
   OR (status='ACEPTADA'
       AND (pickup_mode='now' OR pickup_phase IN ('EN_CAMINO','EN_PUNTO')));

SELECT code,pickup_mode,status::text,pickup_phase
FROM public.reservations
WHERE status='EN_VIAJE'
   OR (status='ACEPTADA'
       AND (pickup_mode='now' OR pickup_phase IN ('EN_CAMINO','EN_PUNTO')))
ORDER BY created_at;