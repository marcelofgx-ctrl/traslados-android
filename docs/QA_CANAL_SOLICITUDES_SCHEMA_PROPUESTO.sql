-- Quick Trip v90 / QA request channel — DRAFT ONLY (2026-10-26)
-- IMPORTANT: do NOT run on the operational Traslados Supabase project.
-- Await an independently created and VERIFIED Quick Trip QA Supabase project.
-- Kept outside supabase/migrations so it cannot deploy automatically.
-- Every account is created fresh using the QA project's independent Auth.
--
-- REQUEST LIFECYCLE: PENDIENTE -> ACEPTADA -> EN_CAMINO
-- -> EN_PUNTO -> EN_VIAJE -> FINALIZADA
-- or PENDIENTE -> RECHAZADA/CANCELADA.
--
-- Mutations must be performed server-side only. Never expose the service role
-- key, allow client-side table mutation, or use production driver credentials.

begin;

-- QA identities come from the NEW project's auth.users, not production.
create table if not exists public.qt_qa_members (
  user_id uuid primary key references auth.users(id) on delete cascade,
  actor_role text not null check (actor_role in ('passenger','driver')),
  active boolean not null default true,
  created_at timestamptz not null default now()
);

create table if not exists public.qt_qa_requests (
  id uuid primary key default gen_random_uuid(),
  passenger_user_id uuid not null references public.qt_qa_members(user_id),
  driver_user_id uuid references public.qt_qa_members(user_id),
  idempotency_key uuid not null,
  state text not null default 'PENDIENTE'
    check (state in ('PENDIENTE','ACEPTADA','RECHAZADA','CANCELADA',
                    'EN_CAMINO','EN_PUNTO','EN_VIAJE','FINALIZADA')),
  state_version integer not null default 1 check (state_version > 0),
  origin_label text not null check (length(origin_label) between 1 and 180),
  destination_label text not null check (length(destination_label) between 1 and 180),
  origin_lat double precision not null check (origin_lat between -35.5 and -30),
  origin_lng double precision not null check (origin_lng between -59.5 and -52),
  destination_lat double precision not null check (destination_lat between -35.5 and -30),
  destination_lng double precision not null check (destination_lng between -59.5 and -52),
  quoted_fare_uyu numeric(11,2) check (quoted_fare_uyu between 0 and 500000),
  qa_only boolean not null default true check (qa_only = true),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  expires_at timestamptz not null default (now() + interval '20 minutes'),
  constraint qt_qa_requests_idempotence_uq unique (passenger_user_id,idempotency_key)
);

create index if not exists qt_qa_requests_pending_ix
  on public.qt_qa_requests(state,created_at desc);

create table if not exists public.qt_qa_request_events (
  id uuid primary key default gen_random_uuid(),
  request_id uuid not null references public.qt_qa_requests(id) on delete cascade,
  state_version integer not null check (state_version > 0),
  actor_user_id uuid references public.qt_qa_members(user_id),
  from_state text,
  to_state text not null
    check (to_state in ('PENDIENTE','ACEPTADA','RECHAZADA','CANCELADA',
                       'EN_CAMINO','EN_PUNTO','EN_VIAJE','FINALIZADA')),
  created_at timestamptz not null default now(),
  constraint qt_qa_events_unique_version unique (request_id,state_version)
);

-- Explicit deny-by-default defense, even if Data API is available.
alter table public.qt_qa_members enable row level security;
alter table public.qt_qa_requests enable row level security;
alter table public.qt_qa_request_events enable row level security;

revoke all on public.qt_qa_members from public,anon,authenticated;
revoke all on public.qt_qa_requests from public,anon,authenticated;
revoke all on public.qt_qa_request_events from public,anon,authenticated;

-- Only trusted QA Edge Functions may use the QA project's server key.
grant select,insert,update,delete on public.qt_qa_members to service_role;
grant select,insert,update,delete on public.qt_qa_requests to service_role;
grant select,insert,update,delete on public.qt_qa_request_events to service_role;

comment on table public.qt_qa_members is
  'QA-only auth role binding. No production users, identities, PINs or tokens.';
comment on table public.qt_qa_requests is
  'QA-only synthetic requests. Never insert these in live reservations.';
comment on table public.qt_qa_request_events is
  'QA-only durable state transition history; populated transactionally server-side.';

commit;

-- BEFORE REAL DEPLOYMENT (separate explicit authorization):
-- 1. Verify target Supabase project ID is new/dedicated QA and back it up.
-- 2. Deploy QA Edge Functions for create/list/transition and auth.getUser(jwt).
-- 3. Provision two NEW Auth accounts; assign driver role server-side only.
-- 4. Implement request+initial event in ONE transaction with idempotency,
--    and state transition+event via optimistic version check or row lock.
-- 5. Add rate limits, role/ownership checks, expiry, and replay protection.
-- 6. Test anon/authenticated rejection and that production URL is NEVER used.
-- 7. GPS publication and private position are NOT part of this draft.
