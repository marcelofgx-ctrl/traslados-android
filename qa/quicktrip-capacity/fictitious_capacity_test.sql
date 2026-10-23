-- SELF-CONTAINED, FICTITIOUS PostgreSQL TEST: run only in disposable/local database.
-- This does not access or change public.reservations or Supabase production.
BEGIN;
CREATE TEMP TABLE qt_fake_bookings (id integer PRIMARY KEY, immediate boolean NOT NULL, state text NOT NULL);
CREATE OR REPLACE FUNCTION pg_temp.qt_fake_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.immediate AND NEW.state IN ('ACEPTADA','EN_VIAJE') AND
     (TG_OP='INSERT' OR OLD.state NOT IN ('ACEPTADA','EN_VIAJE')) THEN
    PERFORM pg_advisory_xact_lock(hashtextextended('qt_fake_driver',0));
    IF EXISTS (SELECT 1 FROM qt_fake_bookings r WHERE r.id<>NEW.id
               AND r.immediate AND r.state IN ('ACEPTADA','EN_VIAJE')) THEN
      RAISE EXCEPTION 'QT_CONDUCTOR_OCUPADO';
    END IF;
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER fake_capacity BEFORE INSERT OR UPDATE OF state ON qt_fake_bookings
 FOR EACH ROW EXECUTE FUNCTION pg_temp.qt_fake_guard();
INSERT INTO qt_fake_bookings VALUES (1,true,'ACEPTADA');
DO $$ BEGIN
  BEGIN
    INSERT INTO qt_fake_bookings VALUES (2,true,'ACEPTADA');
    RAISE EXCEPTION 'TEST FAIL: second immediate accepted';
  EXCEPTION WHEN raise_exception THEN
    IF SQLERRM <> 'QT_CONDUCTOR_OCUPADO' THEN RAISE; END IF;
  END;
END $$;
INSERT INTO qt_fake_bookings VALUES (3,false,'ACEPTADA'); -- programmed not blocked
UPDATE qt_fake_bookings SET state='EN_VIAJE' WHERE id=1;
DO $$ BEGIN
  BEGIN
    INSERT INTO qt_fake_bookings VALUES (4,true,'ACEPTADA');
    RAISE EXCEPTION 'TEST FAIL: during trip accepted';
  EXCEPTION WHEN raise_exception THEN
    IF SQLERRM <> 'QT_CONDUCTOR_OCUPADO' THEN RAISE; END IF;
  END;
END $$;
UPDATE qt_fake_bookings SET state='FINALIZADA' WHERE id=1;
INSERT INTO qt_fake_bookings VALUES (4,true,'ACEPTADA'); -- now allowed
DO $$ BEGIN
 IF (SELECT count(*) FROM qt_fake_bookings WHERE immediate AND state='ACEPTADA')<>1
 THEN RAISE EXCEPTION 'TEST FAIL: unexpected active count'; END IF;
 RAISE NOTICE 'PASS fictitious capacity: blocked second, preserved scheduled, blocked en viaje, released on closure';
END $$;
ROLLBACK;
