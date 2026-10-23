-- DRAFT V2 — ATOMIC CONSTRAINT, NEVER APPLY TO REAL SUPABASE YET.
-- Explicit authorization in this stage permits ONLY local, fictitious PostgreSQL.
--
-- CANONICAL BOOKING DISCRIMINATOR (web-pasajero/trip-board.js):
--   pickup_mode = 'now' or numeric 1..120 => immediate/deferred minutes
--   pickup_mode = 'schedule'          => programmed (may be fare_mode='fixed')
-- fare_mode='fixed' does NOT mean an immediate request.
--
-- Single-driver capacity is an OPERATIONAL state, NOT a time-window forecast.
-- Bookings reserved for a future scheduled time do not occupy the physical
-- vehicle until pickup_phase EN_CAMINO / EN_PUNTO or status EN_VIAJE.
-- Any other/legacy/unrecognized pickup_mode is treated conservatively as
-- immediate for capacity purposes: do not silently bypass the guard.
--
-- A PostgreSQL unique partial index is stronger and simpler than a row trigger
-- taking an advisory lock AFTER other RPCs may have locked reservation rows.
-- The old trigger proposal could deadlock against lock-first RPCs. The UNIQUE
-- check is engine-managed and prevents two concurrent transactions even if
-- they invoke different RPCs, direct SQL, or older clients.
--
-- IMPORTANT BEFORE ANY FUTURE PRODUCTION MIGRATION:
-- 1) Read-only audit status enum values, pickup_mode counts and grants.
-- 2) Check for pre-existing duplicate active services. The CREATE UNIQUE INDEX
--    MUST fail closed if duplicates exist: NEVER silently delete/finish them.
-- 3) Back up schema/functions and validate RPC exception mapping (SQLSTATE
--    23505 + name qt_one_active_service_uq => user message "Conductor ocupado").
-- 4) For public.reservations.status enum, the terminal labels below must exist
--    (observed in driver_set_status_v2). Review extra statuses before rollout.
-- 5) Separately review scheduled availability conflicts; this index does NOT
--    replace the existing time-window availability RPCs.
--
-- This version intentionally has NO CREATE FUNCTION, SECURITY DEFINER, trigger,
-- or GRANT. No new SQL callable as anon/authenticated.
--
-- Use CREATE UNIQUE INDEX (not CONCURRENTLY) in a controlled maintenance
-- transaction once there are no duplicates; PostgreSQL test demonstrates
-- second concurrent acceptance fails with unique_violation / SQLSTATE 23505.
-- The indexed boolean expression has the same value (false) for every
-- non-demo service, allowing exactly ONE row inside this partial predicate.
CREATE UNIQUE INDEX qt_one_active_service_uq
ON public.reservations ((coalesce(is_demo,false)))
WHERE coalesce(is_demo,false)=false
  AND (
       status = 'EN_VIAJE'
       OR (
         status NOT IN ('PENDIENTE','FINALIZADA','CANCELADA','RECHAZADA')
         AND (
           coalesce(pickup_mode,'') <> 'schedule'
           OR pickup_phase IN ('EN_CAMINO','EN_PUNTO')
         )
       )
  );

-- Capacity guard is independent of planned end time and fare_mode.
-- Rollback: DROP INDEX IF EXISTS public.qt_one_active_service_uq;
-- NOTE: deploying this index requires resolving existing duplicate occupied
-- rows by EXPLICIT authorized workflow. It is intentionally not installed.
