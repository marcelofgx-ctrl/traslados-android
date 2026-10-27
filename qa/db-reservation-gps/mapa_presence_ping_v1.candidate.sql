CREATE OR REPLACE FUNCTION public.mapa_presence_ping_v1(p_device_id uuid, p_device_secret text, p_shift_active boolean, p_shift_paused boolean, p_trip_active boolean, p_manually_busy boolean, p_lat double precision, p_lng double precision, p_accuracy_m real, p_gps_at_ms bigint, p_last_move_at_ms bigint)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  gps_time timestamptz;
  moving_time timestamptz;
  loc_ok boolean:=false;
  recent_pickup_demand boolean:=false;
  linked_reservation jsonb:=null;
begin
  if not exists(select 1 from public.driver_live_presence p
    join public.mapa_trayectos_devices d on d.device_id=p.device_id
    where p.singleton_id=1 and p.enabled and p.device_id=p_device_id
    and d.device_secret=p_device_secret) then
    return jsonb_build_object('ok',false,'reason','not_authorized');
  end if;
  if p_gps_at_ms is not null and p_gps_at_ms>0 then
    gps_time:=to_timestamp(p_gps_at_ms/1000.0);
  end if;
  if p_last_move_at_ms is not null and p_last_move_at_ms>0 then
    moving_time:=to_timestamp(p_last_move_at_ms/1000.0);
  end if;
  loc_ok:=coalesce(p_shift_active,false) and not coalesce(p_shift_paused,false)
    and not coalesce(p_trip_active,false) and not coalesce(p_manually_busy,false)
    and p_lat between -35.3 and -30 and p_lng between -58.8 and -52.9
    and p_accuracy_m between 0 and 45
    and gps_time between now()-interval '90 seconds' and now()+interval '10 seconds'
;
  update public.driver_live_presence set
    shift_active=coalesce(p_shift_active,false),
    shift_paused=coalesce(p_shift_paused,false),
    trip_active=coalesce(p_trip_active,false),
    manually_busy=coalesce(p_manually_busy,false),
    lat=case when loc_ok then p_lat else null end,
    lng=case when loc_ok then p_lng else null end,
    accuracy_m=case when loc_ok then p_accuracy_m else null end,
    gps_recorded_at=case when loc_ok then gps_time else null end,
    last_moving_at=case when loc_ok then moving_time else null end,
    received_at=now(),updated_at=now()
  where singleton_id=1 and device_id=p_device_id;
  -- Demand is derived ONLY from already rate-limited customer/guest ETA gates.
  -- This boolean goes solely to the authorized Mapa device; no passenger
  -- receives coordinates, active visitor IDs or demand history.
  if loc_ok then
    recent_pickup_demand :=
      exists(select 1 from public.driver_eta_public_requests
             where requested_at >= now()-interval '75 seconds')
      or exists(select 1 from public.driver_eta_customer_requests
                where requested_at >= now()-interval '75 seconds');
  end if;
  -- Private reservation tracking piggybacks on the already-authorized
  -- stable Mapa QT GPS heartbeat. Never alters public pickup availability.
  -- The nested routine writes ONLY for exactly one reservation that the
  -- conductor has advanced to EN_CAMINO/EN_PUNTO or EN_VIAJE.
  -- It authenticates the current registered device and validates fresh GPS.
  if coalesce(p_shift_active,false) and not coalesce(p_shift_paused,false)
     and not coalesce(p_manually_busy,false)
     and p_lat between -35.3 and -30 and p_lng between -58.8 and -52.9
     and p_accuracy_m between 0 and 45
     and gps_time between now()-interval '75 seconds' and now()+interval '10 seconds'
  then
    begin
      linked_reservation:=public.mapa_reservation_position_v1(
        p_device_id,p_device_secret,p_shift_active,p_shift_paused,
        p_lat,p_lng,p_accuracy_m,p_gps_at_ms);
    exception when others then
      -- Never interrupt normal GPS / driver availability during an
      -- unrelated reservation tracking failure.
      linked_reservation:=jsonb_build_object('ok',false,'reason','sync_error');
    end;
  end if;
  return jsonb_build_object(
    'ok',true,'eligible',loc_ok,
    'pickupDemandActive',loc_ok and recent_pickup_demand
  );
end;$function$

