-- Quick Trip Mapa: one-time PIN exchange for a revocable device session.
-- Does not change reservation functions or grant new table SELECT to anon.
create table if not exists public.mapa_driver_sessions (
  device_id uuid primary key references public.mapa_trayectos_devices(device_id) on delete cascade,
  token_sha256 text not null unique check (length(token_sha256)=64),
  issued_at timestamptz not null default now(),
  revoked_at timestamptz
);
alter table public.mapa_driver_sessions enable row level security;
revoke all on table public.mapa_driver_sessions from PUBLIC, anon, authenticated;

create or replace function public.driver_pin_valid(p_pin text)
returns boolean
language plpgsql stable security definer
set search_path to 'public','extensions','pg_temp'
as $$
declare h text;
begin
  if p_pin is null then return false; end if;
  -- Device token is 256 bits and stored only as a SHA-256 digest.
  -- Existing legacy PIN still works (for old clients).
  if p_pin like 'mqs1.%' then
    if length(p_pin) not between 40 and 130 then return false; end if;
    return exists (
      select 1 from public.mapa_driver_sessions s
      join public.mapa_trayectos_devices d on d.device_id=s.device_id
      where s.revoked_at is null
        and s.token_sha256=encode(extensions.digest(p_pin,'sha256'),'hex')
        and exists(select 1 from public.driver_access a where a.singleton_id=1 and a.active)
    );
  end if;
  select pin_hash into h from public.driver_access where singleton_id=1 and active;
  if h is null then return false; end if;
  return extensions.crypt(p_pin,h)=h;
end;
$$;

create or replace function public.mapa_presence_consent_v1(
  p_device_id uuid,p_device_secret text,p_pin text,p_enabled boolean
) returns jsonb
language plpgsql security definer
set search_path to 'public','extensions','pg_temp'
as $$
declare token text;
begin
  if p_device_id is null or not exists (
    select 1 from public.mapa_trayectos_devices d
    where d.device_id=p_device_id and d.device_secret=p_device_secret
  ) then raise exception 'device authentication failed'; end if;
  if p_enabled then
    -- Do not allow a previously issued bearer token to enroll other devices.
    -- Enrollment always requires the real, original PIN exactly once.
    if p_pin is null or p_pin like 'mqs1.%' or not public.driver_pin_valid(p_pin) then
      raise exception 'PIN incorrecto'; end if;
    token:='mqs1.' || encode(extensions.gen_random_bytes(32),'hex');
    insert into public.mapa_driver_sessions(device_id,token_sha256,issued_at,revoked_at)
    values(p_device_id,encode(extensions.digest(token,'sha256'),'hex'),now(),null)
    on conflict(device_id) do update
       set token_sha256=excluded.token_sha256,issued_at=now(),revoked_at=null;
    insert into public.driver_live_presence(singleton_id,device_id,enabled,consented_at)
    values(1,p_device_id,true,now())
    on conflict(singleton_id) do update set
       device_id=excluded.device_id,enabled=true,consented_at=now(),
       shift_active=false,shift_paused=false,trip_active=false,
       manually_busy=false,lat=null,lng=null,gps_recorded_at=null,
       last_moving_at=null,received_at=null,accuracy_m=null,updated_at=now();
    return jsonb_build_object('ok',true,'enabled',true,'driverSessionToken',token);
  else
    update public.driver_live_presence set enabled=false,lat=null,lng=null,
      gps_recorded_at=null,received_at=null,last_moving_at=null,
      shift_active=false,updated_at=now()
    where singleton_id=1 and device_id=p_device_id;
    -- Turning location sharing OFF does not revoke the driver session.
    return jsonb_build_object('ok',true,'enabled',false);
  end if;
end;
$$;
-- Existing roles may call authorized RPC, but never query session digests.
revoke all on function public.driver_pin_valid(text) from PUBLIC;
grant execute on function public.driver_pin_valid(text) to anon, authenticated, service_role;
revoke all on function public.mapa_presence_consent_v1(uuid,text,text,boolean) from PUBLIC;
grant execute on function public.mapa_presence_consent_v1(uuid,text,text,boolean) to anon, authenticated, service_role;
