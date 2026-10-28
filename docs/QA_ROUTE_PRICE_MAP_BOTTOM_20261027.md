# Quick Trip RC1 · Restore price and relocate map controls · QA

Source: qa/quicktrip-map-reference-20261027 @ ab0808073a51ad7a9ac42aa6d16df20adf3165fd.

## Critical root cause
The design branch accidentally dropped web-pasajero/vercel.json and the same-origin
routing adapter from an earlier QA branch. On Vercel the direct Cloudflare route
API is cross-origin and rejected by the Worker CORS policy, leaving "Sin estimación"
and "A confirmar" despite having two selected addresses.

## Minimal repair
- QA Vercel HTTPS host only uses /api/testing-road with exact external rewrite
  to unchanged Cloudflare route-estimate endpoint. No arbitrary proxy, no CORS
  widening and no changes to production Worker.
- Public GitHub Pages continues calling the original Cloudflare URL.
- Supabase public_reference_quote_v1 supplies the numeric UYU quote only after
  a verified road route. Never calculate fake distances or substitute guessed
  prices.
- QA script/cache versions bumped to avoid old PWA frontend.
- Map controls relocated to bottom right; attribution bottom left. Increase
  visible viewport tile maximum 12→25 only for the visible map, no bulk fetch.

## Must pass before alias update
1. Browser QA 390px and 412px, controls bottom, zoom/pan/reset and page scroll.
2. Original reservation and GPS tracking negative regression tests.
3. Route-price mock verifies only QA Vercel calls same-origin route + real quote
   contract and public GitHub Pages continues using the original Worker.
4. Read-only live calls to the Worker route and public UYU price RPC succeed.
5. New isolated Vercel Preview returns route data through /api/testing-road.
6. Old preview deployment remains available for immediate alias rollback.

## Not included
No Android, production GitHub Pages, Supabase schema/functions, Cloudflare
production, booking side effects, changes to GPS capture, or new billing.
A physical user acceptance of the route and map on Samsung still matters.
