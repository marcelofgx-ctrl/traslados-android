# ETA CORS recovery · 2026-10-27

Original deployed Supabase Edge Function pickup-eta v6 is preserved byte-for-byte as `pickup-eta-v6-rollback.ts`.

Candidate changes only add two exact Vercel Testing HTTPS origins to the existing browser CORS allowlist. No authorization, business logic, GPS, SQL, secrets or routing changes.

Rollback: redeploy the ORIGINAL `pickup-eta-v6-rollback.ts` as the Supabase `pickup-eta` Edge Function with verify_jwt=false. Restore any previous version only after verifying the current deployed code and recording evidence.

Protected main and published Mapa QT remain untouched.
