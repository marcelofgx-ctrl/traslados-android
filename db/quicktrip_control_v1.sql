-- Quick Trip control plane, isolated from passenger/driver data.
-- GitHub public workflows are observed; no GitHub/API secrets are stored here.
create schema if not exists quicktrip_control;
revoke all on schema quicktrip_control from public, anon, authenticated;

create table if not exists quicktrip_control.modules (
  module_key text primary key check (module_key in ('pasajero_web','mapa_integrado')),
  label text not null,
  qa_status text not null default 'physical_pending'
    check (qa_status in ('physical_pending','testing','validated','blocked')),
  last_workflow text,
  last_result text,
  last_run_id bigint,
  last_run_sha text,
  last_run_finished_at timestamptz,
  last_deployed_sha text,
  last_deployed_at timestamptz,
  last_deployed_run_id bigint,
  updated_at timestamptz not null default now()
);
create table if not exists quicktrip_control.github_runs (
  run_id bigint not null,
  attempt integer not null check (attempt>=1),
  module_key text not null references quicktrip_control.modules(module_key),
  workflow text not null,
  stage text not null check (stage in ('ci','qa_build','release_build','pages_deploy')),
  branch text not null,
  commit_sha text not null check (length(commit_sha)=40),
  conclusion text not null,
  finished_at timestamptz not null,
  run_url text not null,
  captured_at timestamptz not null default now(),
  primary key(run_id,attempt)
);
create index if not exists quicktrip_runs_module_time
  on quicktrip_control.github_runs(module_key,finished_at desc);

create table if not exists quicktrip_control.incidents (
  incident_id bigint generated always as identity primary key,
  module_key text not null references quicktrip_control.modules(module_key),
  title text not null,
  status text not null default 'open'
    check (status in ('open','testing','resolved','wontfix')),
  severity text not null default 'normal'
    check (severity in ('low','normal','high','critical')),
  notes text not null default '',
  linked_run_id bigint,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(module_key,title)
);
create table if not exists quicktrip_control.change_batches (
  batch_id bigint generated always as identity primary key,
  module_key text not null references quicktrip_control.modules(module_key),
  title text not null,
  state text not null default 'planned'
    check (state in ('planned','in_progress','ci_passed','published','qa_validated','blocked')),
  pr_number integer,
  commit_sha text,
  notes text not null default '',
  updated_at timestamptz not null default now(),
  unique(module_key,title)
);
alter table quicktrip_control.modules enable row level security;
alter table quicktrip_control.github_runs enable row level security;
alter table quicktrip_control.incidents enable row level security;
alter table quicktrip_control.change_batches enable row level security;
revoke all on all tables in schema quicktrip_control from public, anon, authenticated;
revoke all on all sequences in schema quicktrip_control from public, anon, authenticated;
insert into quicktrip_control.modules(module_key,label) values
  ('pasajero_web','Quick Trip Web/PWA'),
  ('mapa_integrado','Mapa Trayectos con Conductor integrado')
on conflict(module_key) do nothing;

-- Sync only completed GitHub Actions. A green CI is not a physical QA.
-- Only successful GitHub Pages deployments set last_deployed_* fields.
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
  run_id bigint;
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
    run_id := (item->>'id')::bigint;
    attempt_no:=greatest(coalesce((item->>'run_attempt')::integer,1),1);
    finished:=nullif(item->>'updated_at','')::timestamptz;
    if finished is null then continue; end if;
    conclusion_name:=coalesce(item->>'conclusion','unknown');
    branch_name:=coalesce(item->>'head_branch','unknown');
    insert into quicktrip_control.github_runs(
      run_id,attempt,module_key,workflow,stage,branch,commit_sha,
      conclusion,finished_at,run_url
    ) values(
      run_id,attempt_no,module_name,workflow_name,stage_name,branch_name,run_sha,
      conclusion_name,finished,coalesce(item->>'html_url','')
    )
    on conflict(run_id,attempt) do update
      set conclusion=excluded.conclusion,finished_at=excluded.finished_at,
          captured_at=now();
    -- Latest finished GitHub workflow. Never claim physical testing from CI.
    update quicktrip_control.modules
      set last_workflow=workflow_name,last_result=conclusion_name,
        last_run_id=run_id,last_run_sha=run_sha,
        last_run_finished_at=finished,updated_at=now()
      where module_key=module_name
        and (last_run_finished_at is null or last_run_finished_at < finished);
    if stage_name='pages_deploy' and branch_name='main' and conclusion_name='success' then
      update quicktrip_control.modules
        set last_deployed_sha=run_sha,last_deployed_at=finished,
          last_deployed_run_id=run_id,updated_at=now()
        where module_key='pasajero_web'
          and (last_deployed_at is null or last_deployed_at < finished);
    end if;
    n:=n+1;
  end loop;
  return n;
end;
$$;
revoke all on function quicktrip_control.refresh_public_github_runs()
  from public,anon,authenticated;
insert into quicktrip_control.incidents(module_key,title,severity,notes) values
('mapa_integrado','Validar instalación QA en Samsung','high',
 'Instalación física y Aceptar/Rechazar sin confirmar; no borrar app oficial'),
('pasajero_web','Validar ETA tras aceptar viaje','high',
 'Verificar en teléfono estado del conductor, distancia y tiempo al origen con GPS reciente')
on conflict(module_key,title) do nothing;
