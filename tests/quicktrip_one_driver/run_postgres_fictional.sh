#!/usr/bin/env bash
# READ-ONLY against production: this script must run ONLY on disposable local PostgreSQL.
set -euo pipefail
: "${QT_EPHEMERAL_POSTGRES_ONLY:?Must explicitly confirm isolated ephemeral PostgreSQL}"
[ "$QT_EPHEMERAL_POSTGRES_ONLY" = "YES_FAKE_DATA" ] || exit 94
[ "${PGDATABASE:-}" = "qt_fictional" ] || { echo 'Refusing non-fixture DB'; exit 95; }
PSQL=(psql -X -q -v ON_ERROR_STOP=1 -v VERBOSITY=verbose)
run(){ "${PSQL[@]}" -Atc "$1"; }
check(){ local got; got="$(run "$1")"; [ "$got" = "$2" ] || { echo "Expected [$2], received [$got]"; exit 1; }; }
expect_unique_error(){
  local script="$1"
  if "${PSQL[@]}" -c "$script" >/tmp/qt_expected_stdout.log 2>/tmp/qt_expected_error.log; then
    echo "FAIL: a second overlapping service was accepted"; exit 2
  fi
  grep -Eq '23505|duplicate key|quicktrip_one_operational_service_v1_uq' /tmp/qt_expected_error.log || {
    cat /tmp/qt_expected_error.log; exit 3;
  }
}
echo 'Building ONLY fictional schema, never production'
"${PSQL[@]}" -f tests/quicktrip_one_driver/fake_schema.sql
"${PSQL[@]}" -f proposals/quicktrip_single_driver_v1/02_guard_PROPOSAL_NOT_EXECUTE.sql
check "select count(*) from pg_indexes where schemaname='public' and indexname='quicktrip_one_operational_service_v1_uq';" "1"

echo 'Running two racing transactions: one may commit, one must fail'
(
  "${PSQL[@]}" -c "BEGIN;
    UPDATE public.reservations SET status='ACEPTADA' WHERE code='FAKE-IMMEDIATE-A';
    SELECT pg_sleep(1.5);
    COMMIT;" > /tmp/qt_first.log 2>&1
) &
first_pid=$!
sleep 0.20
expect_unique_error "UPDATE public.reservations SET status='ACEPTADA' WHERE code='FAKE-IMMEDIATE-B';"
wait "$first_pid" || { cat /tmp/qt_first.log; exit 4; }
check "SELECT count(*) FROM public.reservations WHERE pickup_mode='now' AND status='ACEPTADA';" "1"
check "SELECT status::text FROM public.reservations WHERE code='FAKE-IMMEDIATE-B';" "PENDIENTE"
expect_unique_error "UPDATE public.reservations SET status='CONFIRMADA' WHERE code='FAKE-IMMEDIATE-B';"
expect_unique_error "UPDATE public.reservations SET status='ACEPTADA_CLIENTE' WHERE code='FAKE-IMMEDIATE-B';"

# Idempotence: re-confirming the same accepted row does not occupy a new slot.
run "UPDATE public.reservations SET status='ACEPTADA' WHERE code='FAKE-IMMEDIATE-A';" >/dev/null

# A scheduled future accepted trip DOES NOT occupy a physical service yet.
run "UPDATE public.reservations SET status='ACEPTADA' WHERE code='FAKE-SCHEDULED';" >/dev/null
run "UPDATE public.reservations SET status='ACEPTADA' WHERE code='FAKE-SCHEDULED-FUTURE';" >/dev/null
check "SELECT count(*) FROM public.reservations WHERE status='ACEPTADA';" "3"

# But dispatching the scheduled driver blocks while immediate is still open.
expect_unique_error "UPDATE public.reservations SET pickup_phase='EN_CAMINO' WHERE code='FAKE-SCHEDULED';"

# A clock/estimated duration NEVER closes accepted service automatically.
check "SELECT count(*) FROM public.reservations WHERE pickup_mode='now' AND status='ACEPTADA' AND route_duration_min=18;" "1"
run "UPDATE public.reservations SET status='FINALIZADA' WHERE code='FAKE-IMMEDIATE-A';" >/dev/null
run "UPDATE public.reservations SET pickup_phase='EN_CAMINO' WHERE code='FAKE-SCHEDULED';" >/dev/null
expect_unique_error "UPDATE public.reservations SET status='ACEPTADA' WHERE code='FAKE-IMMEDIATE-B';"
run "UPDATE public.reservations SET status='EN_VIAJE' WHERE code='FAKE-SCHEDULED';" >/dev/null
expect_unique_error "UPDATE public.reservations SET status='ACEPTADA' WHERE code='FAKE-IMMEDIATE-B';"
run "UPDATE public.reservations SET status='FINALIZADA' WHERE code='FAKE-SCHEDULED';" >/dev/null
run "UPDATE public.reservations SET status='ACEPTADA' WHERE code='FAKE-IMMEDIATE-B';" >/dev/null
check "SELECT count(*) FROM public.reservations WHERE pickup_mode='now' AND status='ACEPTADA';" "1"

# Closing/rejecting also frees capacity, but historical rows are preserved.
run "UPDATE public.reservations SET status='CANCELADA' WHERE code='FAKE-IMMEDIATE-B';" >/dev/null
check "SELECT count(*) FROM public.reservations WHERE status='CANCELADA';" "1"

# Rollback must remove only index; never delete or mutate reservations.
"${PSQL[@]}" -f proposals/quicktrip_single_driver_v1/03_rollback_PROPOSAL_NOT_EXECUTE.sql
check "SELECT count(*) FROM pg_indexes WHERE indexname='quicktrip_one_operational_service_v1_uq';" "0"
run "UPDATE public.reservations SET status='ACEPTADA' WHERE code='FAKE-IMMEDIATE-A';" >/dev/null
run "UPDATE public.reservations SET status='ACEPTADA' WHERE code='FAKE-IMMEDIATE-B';" >/dev/null
check "SELECT count(*) FROM public.reservations WHERE pickup_mode='now' AND status='ACEPTADA';" "2"

# Existing duplicate active rows must BLOCK re-activation. No auto cleanup.
if "${PSQL[@]}" -f proposals/quicktrip_single_driver_v1/02_guard_PROPOSAL_NOT_EXECUTE.sql > /tmp/qt_dirty_stdout.log 2> /tmp/qt_dirty_error.log; then
  echo 'FAIL: guard installed despite two real-style unclosed services'; exit 5
fi
grep -q 'QT_PRECHECK_MULTIPLE_ACTIVE' /tmp/qt_dirty_error.log || { cat /tmp/qt_dirty_error.log; exit 6; }
check "SELECT count(*) FROM pg_indexes WHERE indexname='quicktrip_one_operational_service_v1_uq';" "0"
check "SELECT count(*) FROM public.reservations;" "4"
echo 'PASS: PostgreSQL atomic race, one service, scheduled handoff, idempotence, stale ETA, rollback, dirty-data safety'
