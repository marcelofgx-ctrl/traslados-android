-- Synthetic fixture for disposable PostgreSQL only.
DO $$ BEGIN
 IF current_database() <> 'qt_capacity_test' THEN
   RAISE EXCEPTION 'Local QA database required';
 END IF;
END $$;
CREATE SCHEMA qa_capacity;
CREATE TABLE qa_capacity.fake_reservations(
 id uuid PRIMARY KEY,
 pickup_mode text,
 fare_mode text NOT NULL DEFAULT 'fixed',
 status text NOT NULL DEFAULT 'PENDIENTE',
 pickup_phase text,
 is_demo boolean DEFAULT false,
 created_at timestamptz DEFAULT now()
);
