# QA P1.6 — Simplificación de acceso: solo Quick Trip
1 de noviembre de 2026.

## Problema confirmado
El enlace QA autorizado `https://quicktrip-live-qa-only-20261027-marcelofgx-1927.vercel.app/` mostraba la landing antigua "Traslados con Reserva" porque el archivo raíz del repositorio `index.html` servía ese contenido. La aplicación Quick Trip que usa la interfaz premium está en `/web-pasajero/`.

## Cambio mínimo
Esta rama nace exactamente del commit visual QA P1.6 `ee22375f00dc9f71cb3ca633aafe06adce862aec`. Reemplaza **únicamente el archivo raíz** `index.html` por una navegación HTML inmediata `<meta http-equiv="refresh"...>` a `./web-pasajero/`, y muestra un vínculo accesible de respaldo para navegadores que no siguen meta refresh. El cambio no añade JavaScript, credenciales, llamadas de red, API ni reglas de Vercel.

La ruta QA estable sigue siendo la misma. El URL resultante de Quick Trip es:
`https://quicktrip-live-qa-only-20261027-marcelofgx-1927.vercel.app/web-pasajero/`

## Invariantes
- No modifica ningún archivo bajo `web-pasajero/`: Quick Trip P1.6, diseño, conductor, GPS, ETA, rutas, precio, reservas, PWA, caché y controles permanecen bit a bit igual.
- No modifica `main`, producción, GitHub Pages, Supabase, Edge Functions, Cloudflare, Android ni APK.
- No borra la landing de otras ramas ni despliegues existentes; la **elimina del acceso QA habitual** exclusivamente.
- Conservar los despliegues exactos P1.6 y P1.4 para volver a asignar el alias inmediatamente si la validación en Samsung falla.
- No envía reservas reales; sin datos personales y sin costos.

## Pruebas
CI verifica base exacta, allowlist de archivos y contrato del redireccionamiento, además de regresiones visuales P1.4/P1.6. El despliegue preview debe estar READY antes de reasignar el alias QA; el Samsung valida el salto directo a Quick Trip.
