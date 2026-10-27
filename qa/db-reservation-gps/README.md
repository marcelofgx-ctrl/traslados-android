# Quick Trip passenger tracking from stable Mapa QT (2026-10-27)

**Scope:** ONE PostgreSQL function, public.mapa_presence_ping_v1, on existing Supabase. No Android APK, Web, Cloudflare, schema, billing or reservation table changes.

Problem: Mapa QT stable posts authenticated GPS using mapa_presence_ping_v1; the reservation-specific position is only written by new Testing APK using mapa_reservation_position_v1. This forces separate install/PIN and interrupts the user.

Change: After the existing live-presence update, the same authorized/still-live Mapa heartbeat invokes the existing mapa_reservation_position_v1 internally **only** for current precise/fresh GPS when shift active, not paused, and not manually busy. The called function itself requires a unique accepted reservation in pickup EN_CAMINO/EN_PUNTO or EN_VIAJE and matching device secret. Existing customer_reservation_position_v1 verifies the passenger's ownership/token before revealing GPS. No change to public availability or ETA.

Rollback: apply the byte-for-byte `mapa_presence_ping_v1.rollback.sql` as a SQL migration. The rollback function is snapshotted from production before any changes. Capture production function definition and compare before restoring; do not revert somebody else's changes silently.

Mandatory acceptance: current driver shift and public availability continue; no reservation means no writes; a genuine authorized accepted reservation updates reservation_live_position from stable Mapa QT GPS; customer with that reservation sees fresh coordinates in pickup and en viaje; unrelated users do not; revocation and stale GPS stop tracking.

**Physical acceptance pending:** no controlled accepted Quick Trip reservation or real Samsung UI verification exists in this migration. Do not claim Live complete without these.

Never publish the QA Android console or create new passenger APK.