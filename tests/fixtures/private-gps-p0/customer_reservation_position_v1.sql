CREATE OR REPLACE FUNCTION public.customer_reservation_position_v1(p_reservation_id uuid, p_session_token text DEFAULT NULL::text, p_public_token text DEFAULT NULL::text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
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
  if v_loc.reservation_id is null or v_presence.singleton_id is null
     or not coalesce(v_presence.enabled,false)
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
end;$function$

