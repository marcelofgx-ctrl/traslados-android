\set ON_ERROR_STOP on
\echo 'QUICKTRIP 4B — LOCAL POSTGRES ONLY'
-- Every test fixture identity is fictitious. The mock claim is NOT a real JWT.
CREATE TEMP TABLE lab_test_results(name text PRIMARY KEY);
SET ROLE lab_anon;
SET request.jwt.claim.sub = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
DO $f$
BEGIN
  BEGIN
    PERFORM lab_api.driver_session_status_auth_v1();
    RAISE EXCEPTION 'FAIL_ANON_EXECUTED';
  EXCEPTION WHEN insufficient_privilege THEN
    RAISE NOTICE 'PASS anonymous cannot call authenticated API';
  END;
END $f$;
RESET ROLE;

SET ROLE lab_authenticated;
SET request.jwt.claim.sub = '';
DO $f$
DECLARE s jsonb;
BEGIN
 s:=lab_api.driver_session_status_auth_v1();
 IF s->>'authorized'<>'false' THEN RAISE EXCEPTION 'FAIL_MISSING_SESSION'; END IF;
 IF lab_api.driver_list_direct_auth_v1()<>'[]'::jsonb THEN
    RAISE EXCEPTION 'FAIL_MISSING_SESSION_LIST'; END IF;
 BEGIN
   PERFORM lab_api.driver_decide_direct_auth_v1(
     '00000000-0000-0000-0000-000000000001',true,
     '11111111-1111-1111-1111-111111111111');
   RAISE EXCEPTION 'FAIL_UNAUTHORIZED_DECISION';
 EXCEPTION WHEN raise_exception THEN
   IF SQLERRM<>'NOT_AUTHORIZED' THEN RAISE; END IF;
 END;
 RAISE NOTICE 'PASS missing session blocked';
END $f$;

SET request.jwt.claim.sub = 'cccccccc-cccc-cccc-cccc-cccccccccccc';
DO $f$
BEGIN
 IF lab_api.driver_session_status_auth_v1()->>'authorized'<>'false' THEN
    RAISE EXCEPTION 'FAIL_INACTIVE_DRIVER'; END IF;
 IF lab_api.driver_list_direct_auth_v1()<>'[]'::jsonb THEN
    RAISE EXCEPTION 'FAIL_INACTIVE_LIST'; END IF;
 RAISE NOTICE 'PASS inactive driver blocked';
END $f$;

SET request.jwt.claim.sub = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
DO $f$
DECLARE rows jsonb;
BEGIN
 rows:=lab_api.driver_list_direct_auth_v1();
 IF jsonb_array_length(rows)<>1 OR rows->0->>'id'<>'00000000-0000-0000-0000-000000000004' THEN
    RAISE EXCEPTION 'FAIL_DRIVER_B_VISIBILITY: %',rows;
 END IF;
 BEGIN
   PERFORM lab_api.driver_decide_direct_auth_v1(
   '00000000-0000-0000-0000-000000000001',true,
   '11111111-1111-1111-1111-111111111112');
   RAISE EXCEPTION 'FAIL_OTHER_DRIVER_ACCEPTED';
 EXCEPTION WHEN raise_exception THEN
   IF SQLERRM<>'NOT_AUTHORIZED' THEN RAISE; END IF;
 END;
 RAISE NOTICE 'PASS cross-driver access denied';
END $f$;

SET request.jwt.claim.sub = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
DO $f$
DECLARE rows jsonb; result jsonb; again jsonb;
BEGIN
 IF lab_api.driver_session_status_auth_v1()->>'authorized'<>'true' THEN
   RAISE EXCEPTION 'FAIL_ACTIVE_DRIVER'; END IF;
 rows:=lab_api.driver_list_direct_auth_v1();
 IF jsonb_array_length(rows)<>3 THEN RAISE EXCEPTION 'FAIL_LIST_FILTER %',rows; END IF;
 -- Immediate and priced only; excludes scheduled, cancelled, expired, invalid price and other drivers.
 result:=lab_api.driver_decide_direct_auth_v1(
   '00000000-0000-0000-0000-000000000001',true,
   '11111111-1111-1111-1111-111111111111');
 IF result->>'status'<>'ACEPTADA' THEN RAISE EXCEPTION 'FAIL_ACCEPT %',result; END IF;
 again:=lab_api.driver_decide_direct_auth_v1(
   '00000000-0000-0000-0000-000000000001',true,
   '11111111-1111-1111-1111-111111111111');
 IF again<>result THEN RAISE EXCEPTION 'FAIL_IDEMPOTENCY'; END IF;
 BEGIN
   PERFORM lab_api.driver_decide_direct_auth_v1(
    '00000000-0000-0000-0000-000000000001',false,
    '11111111-1111-1111-1111-111111111111');
   RAISE EXCEPTION 'FAIL_REQUEST_ID_REUSED';
 EXCEPTION WHEN raise_exception THEN
   IF SQLERRM<>'IDEMPOTENCY_CONFLICT' THEN RAISE; END IF;
 END;
 BEGIN
   PERFORM lab_api.driver_decide_direct_auth_v1(
    '00000000-0000-0000-0000-000000000001',false,
    '11111111-1111-1111-1111-111111111119');
   RAISE EXCEPTION 'FAIL_STATUS_REWRITTEN';
 EXCEPTION WHEN raise_exception THEN
   IF SQLERRM<>'ALREADY_DECIDED' THEN RAISE; END IF;
 END;
 result:=lab_api.driver_decide_direct_auth_v1(
   '00000000-0000-0000-0000-000000000002',false,
   '11111111-1111-1111-1111-111111111113');
 IF result->>'status'<>'RECHAZADA' THEN RAISE EXCEPTION 'FAIL_REJECT'; END IF;
 RAISE NOTICE 'PASS accept/reject, idempotency, status protection';
END $f$;
DO $f$
DECLARE reservation uuid; expected_error text;
BEGIN
 FOR reservation,expected_error IN
 SELECT * FROM (VALUES
  ('00000000-0000-0000-0000-000000000003'::uuid,'NOT_DIRECT_FIXED'),
  ('00000000-0000-0000-0000-000000000005'::uuid,'EXPIRED'),
  ('00000000-0000-0000-0000-000000000006'::uuid,'NOT_AVAILABLE'),
  ('00000000-0000-0000-0000-000000000008'::uuid,'ALREADY_DECIDED'),
  ('00000000-0000-0000-0000-000000000009'::uuid,'NOT_DIRECT_FIXED')
 ) v(reservation, expected_error)
 LOOP
   BEGIN
    PERFORM lab_api.driver_decide_direct_auth_v1(
       reservation,true,gen_random_uuid());
    RAISE EXCEPTION 'FAIL_NEGATIVE_CASE: %',reservation;
   EXCEPTION WHEN raise_exception THEN
    IF SQLERRM<>expected_error THEN RAISE; END IF;
   END;
 END LOOP;
 RAISE NOTICE 'PASS scheduled/expired/unavailable/cancelled/zero-price protected';
END $f$;

DO $f$
BEGIN
 BEGIN
   UPDATE lab_data.reservations SET status='ACEPTADA' WHERE
    id='00000000-0000-0000-0000-000000000006';
   RAISE EXCEPTION 'FAIL_DIRECT_UPDATE_ALLOWED';
 EXCEPTION WHEN insufficient_privilege THEN
   RAISE NOTICE 'PASS client cannot directly UPDATE reservation';
 END;
END $f$;
RESET ROLE;

DO $f$
DECLARE s record;
BEGIN
 SELECT status,quote_status,confirmed_at INTO s FROM lab_data.reservations
 WHERE id='00000000-0000-0000-0000-000000000001';
 IF s.status<>'ACEPTADA' OR s.quote_status<>'ACEPTADO' OR s.confirmed_at IS NULL THEN
    RAISE EXCEPTION 'FAIL_FIXED_FARE_ATOMIC_ACCEPT'; END IF;
 SELECT status,quote_status INTO s FROM lab_data.reservations
 WHERE id='00000000-0000-0000-0000-000000000002';
 IF s.status<>'RECHAZADA' OR s.quote_status<>'RECHAZADO' THEN
    RAISE EXCEPTION 'FAIL_FIXED_FARE_ATOMIC_REJECT'; END IF;
 IF (SELECT count(*) FROM lab_data.reservation_events)<>2 THEN
    RAISE EXCEPTION 'FAIL_EVENT_DEDUP'; END IF;
 IF (SELECT count(*) FROM lab_data.decision_requests)<>2 THEN
    RAISE EXCEPTION 'FAIL_REQUEST_DEDUP'; END IF;
 IF (SELECT count(*) FROM lab_data.reservation_events
     WHERE actor<>'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa')<>0 THEN
    RAISE EXCEPTION 'FAIL_AUDIT_ACTOR'; END IF;
 RAISE NOTICE 'PASS atomic fixed fare, audit actor, exactly once';
END $f$;

-- Test a trigger rejection separately, without changing any live data.
DO $f$
BEGIN
 BEGIN
   UPDATE lab_data.reservations SET status='ACEPTADA'
   WHERE id='00000000-0000-0000-0000-000000000006';
   RAISE EXCEPTION 'FAIL_FIXED_FARE_TRIGGER';
 EXCEPTION WHEN raise_exception THEN
   IF SQLERRM<>'FIXED_FARE_ACCEPT_INCOMPLETE' THEN RAISE; END IF;
 END;
 RAISE NOTICE 'PASS fixed-fare guard rejects incomplete direct UPDATE';
END $f$;
\echo 'PASS: SQL isolation / actor / RLS / transactions / fixed-fare / idempotency'
