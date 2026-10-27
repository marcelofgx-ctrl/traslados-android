CREATE OR REPLACE FUNCTION public.mapa_reservation_position_v1(p_device_id uuid, p_device_secret text, p_shift_active boolean, p_shift_paused boolean, p_lat double precision, p_lng double precision, p_accuracy_m real, p_gps_at_ms bigint)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
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
end;$function$

