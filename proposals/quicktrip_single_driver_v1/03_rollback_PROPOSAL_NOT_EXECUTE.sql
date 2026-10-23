-- PROPOSAL ONLY. Removes ONLY the singleton guard index.
-- No booking history/status/customer data is deleted or changed.
-- WARNING: once removed, overlapping immediate acceptances are possible again.
BEGIN;
SET LOCAL lock_timeout='5s';
DROP INDEX IF EXISTS public.quicktrip_one_operational_service_v1_uq;
COMMIT;