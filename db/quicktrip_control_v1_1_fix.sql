-- Correct variable/column ambiguity during scheduled sync.
create or replace function quicktrip_control.refresh_public_github_runs()
returns integer language plpgsql security invoker
set search_path = pg_catalog, quicktrip_control, extensions
as $$
declare
  response extensions.http_response;
  payload jsonb;
  item jsonb;
  n integer := 0;
  workflow_name text;
  module_name text;
  stage_name text;
  finished timestamptz;
  run_sha text;
  v_run_id bigint;
  attempt_no integer;
  conclusion_name text;
  branch_name text;
begin
  if not pg_try_advisory_xact_lock(20261020, 29) then
    return 0;
  end if;
  response := extensions.http_get(
    'https://api.github.com/repos/marcelofgx-ctrl/traslados-android/actions/runs?per_page=100'
  );
  if response.status <> 200 then
    raise warning 'QuickTrip control Github HTTP status: %',response.status;
    return 0;
  end if;
  payload:=response.content::jsonb;
  if jsonb_typeof(payload->'workflow_runs') <> 'array' then
    raise warning 'QuickTrip control: unexpected public GitHub payload';
    return 0;
  end if;
  for item in select value from jsonb_array_elements(payload->'workflow_runs')
  loop
    if item->>'status' <> 'completed' then continue; end if;
    workflow_name := item->>'name';
    module_name := case
      when workflow_name in ('pages build and deployment',
        'Verify and package passenger PWA (GitHub Pages via branch)')
        then 'pasajero_web'
      when workflow_name in ('Quick Trip V7 - APKs de testing',
        'Build Mapa Trayectos RELEASE','Mapa Trayectos — Visual QA Android',
        'Verify modular Mapa and reservations core')
        then 'mapa_integrado'
      else null end;
    if module_name is null then continue; end if;
    stage_name := case
      when workflow_name='pages build and deployment' then 'pages_deploy'
      when workflow_name='Quick Trip V7 - APKs de testing' then 'qa_build'
      when workflow_name='Build Mapa Trayectos RELEASE' then 'release_build'
      else 'ci' end;
    run_sha := item->>'head_sha';
    if run_sha is null or length(run_sha)<>40 then continue; end if;
    v_run_id := (item->>'id')::bigint;
    attempt_no:=greatest(coalesce((item->>'run_attempt')::integer,1),1);
    finished:=nullif(item->>'updated_at','')::timestamptz;
    if finished is null then continue; end if;
    conclusion_name:=coalesce(item->>'conclusion','unknown');
    branch_name:=coalesce(item->>'head_branch','unknown');
    insert into quicktrip_control.github_runs(
      run_id,attempt,module_key,workflow,stage,branch,commit_sha,
      conclusion,finished_at,run_url
    ) values(
      v_run_id,attempt_no,module_name,workflow_name,stage_name,branch_name,run_sha,
      conclusion_name,finished,coalesce(item->>'html_url','')
    )
    on conflict(run_id,attempt) do update
      set conclusion=excluded.conclusion,finished_at=excluded.finished_at,
          captured_at=now();
    -- Latest finished GitHub workflow. Never claim physical testing from CI.
    update quicktrip_control.modules
      set last_workflow=workflow_name,last_result=conclusion_name,
        last_run_id=v_run_id,last_run_sha=run_sha,
        last_run_finished_at=finished,updated_at=now()
      where module_key=module_name
        and (last_run_finished_at is null or last_run_finished_at < finished);
    if stage_name='pages_deploy' and branch_name='main' and conclusion_name='success' then
      update quicktrip_control.modules
        set last_deployed_sha=run_sha,last_deployed_at=finished,
          last_deployed_run_id=v_run_id,updated_at=now()
        where module_key='pasajero_web'
          and (last_deployed_at is null or last_deployed_at < finished);
    end if;
    n:=n+1;
  end loop;
  return n;
end;
$$;
