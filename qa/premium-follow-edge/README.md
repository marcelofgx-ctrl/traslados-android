# Premium follow QA: exact Supabase CORS allowance with rollback

Background: the passenger PWA has a stable QA hostname, but the Supabase pickup-eta Edge Function checks the Origin against an exact allowlist. Historically each Vercel Preview deployment URL changed, so ETA broke again.

Source snapshot: Edge Function pickup-eta version 7, verify_jwt=false, unchanged service-role authorization in Deno. Rollback file contains the exact production source before this adjustment.

Change: append ONE exact QA hostname to allowed origins:
https://quicktrip-live-qa-only-20261027-marcelofgx-1927.vercel.app

No wildcards, no new API endpoints, no service credentials, no DB modification, no product motor changes. All other code is byte-identical.

Rollback: redeploy the exact contents of pickup-eta-v7.rollback.ts as the pickup-eta function after checking no newer unrelated change was deployed. Restore the old QA alias to its prior deployment if the preview has problems.

Scope: only new allowed HTTPS Origin, matching the project's private Vercel testing preview. Live route and GPS position policies stay as before.
