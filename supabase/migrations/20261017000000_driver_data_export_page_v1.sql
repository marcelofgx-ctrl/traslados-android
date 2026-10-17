-- Data-only, READ-ONLY paginated export for the driver's private archive.
-- Never exports accounts, password/PIN hashes, device_secret, sessions or settings.
-- No DELETE/TRUNCATE and no background retention job.
create or replace function public.driver_data_export_page_v1(
  p_device_id uuid, p_device_secret text,
  p_pin text, p_dataset text, p_after_cursor text, p_cutoff_ms bigint, p_limit integer
) returns jsonb
language plpgsql security definer set search_path = ''
as $$
declare
  v_table text;
  v_key text;
  v_time_clause text;
  v_rows jsonb;
  v_cursor text;
  v_limit integer;
begin
  if p_pin is null or length(p_pin) not between 4 and 10 or not public.driver_pin_valid(p_pin) then
    raise exception 'No autorizado' using errcode = '28000';
  end if;
  if p_device_id is null or p_device_secret is null
     or length(p_device_secret)<24 or not exists(
       select 1 from public.mapa_trayectos_devices d
       where d.device_id=p_device_id and d.device_secret=p_device_secret
     ) then
    raise exception 'Dispositivo no autorizado' using errcode = '28000';
  end if;
  if p_after_cursor is null or length(p_after_cursor)>160 or p_cutoff_ms is null or p_cutoff_ms <= 0 then
    raise exception 'Parámetros inválidos' using errcode = '22023';
  end if;
  v_limit := least(greatest(coalesce(p_limit,500),1),1000);
  case p_dataset
    when 'jornadas' then
      v_table := 'mapa_trayectos_shifts';
      v_key := '(t.device_id::text || '':'' || t.shift_id::text)';
      v_time_clause := 't.started_at_ms <= $2';
    when 'viajes_mapa' then
      v_table := 'mapa_trayectos_trips';
      v_key := '(t.device_id::text || '':'' || t.trip_id::text)';
      v_time_clause := 't.started_at_ms <= $2';
    when 'puntos_gps' then
      v_table := 'mapa_trayectos_points';
      v_key := '(t.device_id::text || '':'' || t.point_id::text)';
      v_time_clause := 't.recorded_at_ms <= $2';
    when 'reservas' then
      v_table := 'reservations';
      v_key := 't.id::text';
      v_time_clause := 't.created_at <= to_timestamp($2 / 1000.0)';
    when 'eventos_reserva' then
      v_table := 'reservation_events';
      v_key := 't.id::text';
      v_time_clause := 't.created_at <= to_timestamp($2 / 1000.0)';
    when 'paradas_reserva' then
      v_table := 'reservation_stops';
      v_key := 't.id::text';
      v_time_clause := 't.created_at <= to_timestamp($2 / 1000.0)';
    when 'mensajes_reserva' then
      v_table := 'reservation_chat_messages';
      v_key := 't.id::text';
      v_time_clause := 't.created_at <= to_timestamp($2 / 1000.0)';
    else
      raise exception 'Conjunto de datos no permitido' using errcode = '22023';
  end case;

  -- Hardcoded table/column allowlist (never interpolate caller-supplied SQL).
  execute format(
     'select coalesce(jsonb_agg(to_jsonb(s) - ''__cursor'' order by s.__cursor), ''[]''::jsonb), max(s.__cursor)
        from (select t.*, %1$s as __cursor from public.%2$I t
              where %1$s > $1 and %3$s order by %1$s limit $3) s',
      v_key,v_table,v_time_clause)
    into v_rows,v_cursor
    using p_after_cursor,p_cutoff_ms,v_limit;
  return jsonb_build_object(
    'dataset',p_dataset,'items',v_rows,'next_cursor',coalesce(v_cursor,''),
    'page_size',jsonb_array_length(v_rows),
    'has_more',jsonb_array_length(v_rows)=v_limit);
end;
$$;
revoke all on function public.driver_data_export_page_v1(uuid,text,text,text,text,bigint,integer) from public;
grant execute on function public.driver_data_export_page_v1(uuid,text,text,text,text,bigint,integer) to anon,authenticated,service_role;
comment on function public.driver_data_export_page_v1(uuid,text,text,text,text,bigint,integer)
  is 'Solo lectura; exporta historial en bloques tras validar PIN del conductor. No elimina datos.';
