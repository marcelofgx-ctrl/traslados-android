-- ROLLBACK ONLY — requires separate approval; preserves all reservations.
BEGIN;
DROP TRIGGER IF EXISTS qt_guard_one_immediate_open_v1 ON public.reservations;
DROP FUNCTION IF EXISTS public.qt_guard_one_immediate_open_v1();
COMMIT;
