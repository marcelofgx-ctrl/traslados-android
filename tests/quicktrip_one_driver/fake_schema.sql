-- Disposable local PostgreSQL only. Every reservation below is fictitious.
CREATE TYPE public.reservation_status AS ENUM
  ('PENDIENTE','ACEPTADA','EN_VIAJE','FINALIZADA','CANCELADA','RECHAZADA',
   'PRESUPUESTO_ENVIADO','ACEPTADA_CLIENTE','CONFIRMADA','RECHAZADA_CLIENTE');
CREATE TABLE public.reservations (
  id uuid PRIMARY KEY,
  code text UNIQUE NOT NULL,
  pickup_mode text NOT NULL CHECK(pickup_mode IN ('now','schedule')),
  pickup_phase text NOT NULL DEFAULT 'SIN_INICIAR',
  status public.reservation_status NOT NULL DEFAULT 'PENDIENTE',
  created_at timestamptz NOT NULL DEFAULT now(),
  route_duration_min integer NOT NULL DEFAULT 18
);
INSERT INTO public.reservations(id,code,pickup_mode,pickup_phase,status)
VALUES
('00000000-0000-4000-8000-000000000001','FAKE-IMMEDIATE-A','now','SIN_INICIAR','PENDIENTE'),
('00000000-0000-4000-8000-000000000002','FAKE-IMMEDIATE-B','now','SIN_INICIAR','PENDIENTE'),
('00000000-0000-4000-8000-000000000003','FAKE-SCHEDULED','schedule','SIN_INICIAR','PENDIENTE'),
('00000000-0000-4000-8000-000000000004','FAKE-SCHEDULED-FUTURE','schedule','SIN_INICIAR','PENDIENTE');