-- Resume previously authorized GPS sharing without asking for driver PIN again.
-- Requires both the Mapa installation's private device secret and revocable session token.
create or replace function public.mapa_presence_resume_v1(
 p_device_id uuid, p_device_secret text, p_session_token text
) returns jsonb
language plpgsql security definer
set search_path to 'public','extensions','pg_temp'
as $$
begin
  if p_device_id is null or p_session_token is null or p_session_token not like 'mqs1.%'
     or not exists (
       select 1 from public.mapa_trayectos_devices d
       join public.mapa_driver_sessions s on s.device_id=d.device_id
       where d.device_id=p_device_id and d.device_secret=p_device_secret
         and s.revoked_at is null
         and s.token_sha256=encode(extensions.digest(p_session_token,'sha256'),'hex')
     ) or not exists (select 1 from public.driver_access where singleton_id=1 and active)
  then raise exception 'device session not authorized'; end if;
  insert into public.driver_live_presence(singleton_id,device_id,enabled,consented_at)
  values(1,p_device_id,true,now())
  on conflict(singleton_id) do update set
     device_id=excluded.device_id,enabled=true,consented_at=now(),
     shift_active=false,shift_paused=false,trip_active=false,
     manually_busy=false,lat=null,lng=null,gps_recorded_at=null,
     last_moving_at=null,received_at=null,accuracy_m=null,updated_at=now();
  return jsonb_build_object('ok',true,'enabled',true);
end;
$$;
revoke all on function public.mapa_presence_resume_v1(uuid,text,text) from PUBLIC;
grant execute on function public.mapa_presence_resume_v1(uuid,text,text) to anon,authenticated,service_role;