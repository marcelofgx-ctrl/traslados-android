-- Scheduled read-only public GitHub ingest (into private control metadata only).
create extension if not exists pg_cron with schema pg_catalog;
select cron.schedule(
  'quicktrip-public-github-control',
  '*/30 * * * *',
  $$select quicktrip_control.refresh_public_github_runs();$$
);