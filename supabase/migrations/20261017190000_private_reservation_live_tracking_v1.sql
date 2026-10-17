-- Private, ephemeral in-app vehicle position for one authorized ACTIVE reservation.
-- Additive: never deletes GPS journeys, reservations or historic points.
-- Does NOT change the public availability heartbeat or expose driver_live_presence.
create table if not exists public.reservation_live_position (
  reservation_id uuid primary key references public.reservations(id),
  device_id uuid not null,
  lat double precision not null check(lat between -35.3 and -30),
  lng double precision not null check(lng between -58.8 and -52.9),
  accuracy_m real not null check(accuracy_m between 0 and 45),
  gps_recorded_at timestamptz not null,
  received_at timestamptz not null default now()
);
alter table public.reservation_live_position enable row level security;
revoke all on public.reservation_live_position from public, anon, authenticated;

-- Called only by an opt-in, registered Mapa device. The driver's existing
-- GPS history remains untouched. Select one active ride, never guess between
-- two concurrent bookings. A scheduled confirmed ride alone is NOT "en camino".
create or replace function public.mapa_reservation_position_v1(
  p_device_id uuid, p_device_secret text,
  p_shift_active boolean, p_shift_paused boolean,
  p_lat double precision, p_lng double precision,
  p_accuracy_m real, p_gps_at_ms bigint
) returns jsonb language plpgsql security definer
set search_path to 'public','pg_temp'
as $function$
declare
  v_id uuid;
  v_count integer;
  v_gps timestamptz;
begin
  if not exists (
    select 1 from public.driver_live_presence p
    join public.mapa_trayectos_devices d on d.device_id=p.device_id
    where p.singleton_id=1 and p.enabled
      and p.device_id=p_device_id
      and d.device_secret=p_device_secret
  ) then return jsonb_build_object('ok',false,'reason','unauthorized'); end if;

  if not coalesce(p_shift_active,false) or coalesce(p_shift_paused,true)
     or p_lat not between -35.3 and -30 or p_lng not between -58.8 and -52.9
     or p_accuracy_m not between 0 and 45 or p_gps_at_ms is null
     or p_gps_at_ms<=0 then
    return jsonb_build_object('ok',false,'reason','gps_unavailable');
  end if;
  v_gps:=to_timestamp(p_gps_at_ms/1000.0);
  if v_gps not between now()-interval '75 seconds' and now()+interval '10 seconds' then
    return jsonb_build_object('ok',false,'reason','stale_gps');
  end if;

  select count(*),min(id) into v_count,v_id
  from (
    select id from public.reservations
    where status::text='EN_VIAJE' or
      (status::text in ('ACEPTADA','ACEPTADA_CLIENTE','CONFIRMADA')
         and pickup_phase in ('EN_CAMINO','EN_PUNTO'))
    limit 2
  ) active_booking;
  if v_count<>1 then
    return jsonb_build_object('ok',false,'reason','no_unique_active_reservation');
  end if;

  insert into public.reservation_live_position
    (reservation_id,device_id,lat,lng,accuracy_m,gps_recorded_at,received_at)
  values (v_id,p_device_id,p_lat,p_lng,p_accuracy_m,v_gps,now())
  on conflict(reservation_id) do update set
    device_id=excluded.device_id,lat=excluded.lat,lng=excluded.lng,
    accuracy_m=excluded.accuracy_m,gps_recorded_at=excluded.gps_recorded_at,
    received_at=excluded.received_at
  where public.reservation_live_position.gps_recorded_at<=excluded.gps_recorded_at;
  return jsonb_build_object('ok',true,'updated',true);
end;$function$;

-- Never return GPS outside an authenticated, owned reservation or a valid
-- legacy 48+-character reservation token. No coordinates after completion,
-- revocation, account change, loss of shift consent or stale GPS.
create or replace function public.customer_reservation_position_v1(
  p_reservation_id uuid, p_session_token text default null,
  p_public_token text default null
) returns jsonb language plpgsql security definer
set search_path to 'public','pg_temp'
as $function$
declare
  v_res public.reservations%rowtype;
  v_customer uuid;
  v_loc public.reservation_live_position%rowtype;
  v_presence public.driver_live_presence%rowtype;
  v_phase text;
begin
  if p_reservation_id is null then
    return jsonb_build_object('available',false,'reason','unauthorized');
  end if;
  if nullif(btrim(coalesce(p_session_token,'')),'') is not null then
    v_customer:=public.availability_session_customer(p_session_token);
    if v_customer is null then
      return jsonb_build_object('available',false,'reason','unauthorized');
    end if;
    select * into v_res from public.reservations
    where id=p_reservation_id and customer_id=v_customer;
  else
    if length(coalesce(p_public_token,''))<48 then
      return jsonb_build_object('available',false,'reason','unauthorized');
    end if;
    select * into v_res from public.reservations
    where id=p_reservation_id and public_token=p_public_token;
  end if;
  if not found then return jsonb_build_object('available',false,'reason','unauthorized'); end if;

  if v_res.status::text not in ('ACEPTADA','ACEPTADA_CLIENTE','CONFIRMADA','EN_VIAJE') then
    return jsonb_build_object('available',false,'reason','not_active');
  end if;
  v_phase:=case
    when v_res.status::text='EN_VIAJE' then 'EN_VIAJE'
    else coalesce(v_res.pickup_phase,'SIN_INICIAR') end;
  if v_phase not in ('EN_CAMINO','EN_PUNTO','EN_VIAJE') then
    return jsonb_build_object('available',false,'phase',v_phase,'reason','preparing_pickup');
  end if;

  select * into v_loc from public.reservation_live_position
  where reservation_id=p_reservation_id;
  select * into v_presence from public.driver_live_presence where singleton_id=1;
  if not found or not coalesce(v_presence.enabled,false)
     or not coalesce(v_presence.shift_active,false)
     or coalesce(v_presence.shift_paused,true)
     or v_presence.device_id is distinct from v_loc.device_id
     or v_loc.received_at<now()-interval '75 seconds'
     or v_loc.gps_recorded_at<now()-interval '75 seconds'
     or v_loc.gps_recorded_at>now()+interval '10 seconds'
     or v_loc.accuracy_m>45 then
    return jsonb_build_object('available',false,'phase',v_phase,'reason','position_unavailable');
  end if;
  return jsonb_build_object(
    'available',true,'phase',v_phase,
    'driverLat',v_loc.lat,'driverLng',v_loc.lng,
    'gpsAgeSec',greatest(0,floor(extract(epoch from (now()-v_loc.gps_recorded_at)))::integer),
    'accuracyM',v_loc.accuracy_m
  );
end;$function$;

revoke all on function public.mapa_reservation_position_v1(uuid,text,boolean,boolean,double precision,double precision,real,bigint) from public;
revoke all on function public.customer_reservation_position_v1(uuid,text,text) from public;
grant execute on function public.mapa_reservation_position_v1(uuid,text,boolean,boolean,double precision,double precision,real,bigint) to anon,authenticated;
grant execute on function public.customer_reservation_position_v1(uuid,text,text) to anon,authenticated;
