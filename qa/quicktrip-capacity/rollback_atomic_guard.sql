-- DRAFT ROLLBACK: execute ONLY after explicit approval for production.
-- Does not modify, cancel, finalize, or delete reservation records.
-- Running in local/fictitious PostgreSQL is authorized separately.
BEGIN;
DROP INDEX IF EXISTS public.qt_one_active_service_uq;
COMMIT;

-- Post-rollback verification:
-- SELECT to_regclass('public.qt_one_active_service_uq') IS NULL AS index_removed;
-- SELECT COUNT(*) FROM public.reservations; -- must equal pre-rollback count
-- Keep the previously exported schema and GRANT manifest for audit.
