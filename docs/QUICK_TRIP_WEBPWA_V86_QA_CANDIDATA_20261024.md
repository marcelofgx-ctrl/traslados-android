# Quick Trip — Web y PWA, candidata canónica v86 (SOLO QA)

**Base:** `qa/quicktrip-blindaje-fase1-canonico-20261024@dcc95549600763a4e52978f92f283edc2694583d`  
**Rama:** `qa/quicktrip-webpwa-canonica-v86-20261024`.  
**Referencias:** `docs/PRODUCTO_CANONICO_QUICKTRIP.md` y `docs/quicktrip-canonical-contract.json`.  
**Estado:** **NO PUBLICADA**, sin autorización de fusionar, producción, Supabase ni reserva real.

## Qué contiene esta candidata

- Una única Web con manifiesto y Service Worker PWA; no duplicamos el código entre web y PWA.
- Portada V6 recuperada de `qa/quicktrip-v85-visual-v6-circulos-samsung-20261024`: ciudad y 3 botones circulares.
- Tu Viaje de v85 compacto, conservar datos en recarga y corregir Atrás. Pantalla de programados separada.
- `quicktrip-canonical-v86.css`: único **archivo propietario** del skin Quick Trip, que concentra CSS funcional v85 y cierres visuales V6; no seguir cargando `quicktrip-home-qa.css` ni `quicktrip-approved-v85-qa.css`. Los estilos estructurales originales `styles.css` y `premium-route.css` siguen cargándose porque pertenecen a los motores ya establecidos.
- Selector de modo claro/oscuro añadido a la capa de presentación (solo almacenamiento local).
- Modalidad segura para QA visible: banner QA, meta `traslados-preview=read-only`, CSP `connect-src 'none'`, sin llamadas telefónicas activas ni solicitudes al backend.
- La **copia real que el usuario recibe es sólo lectura**; las pruebas de RPC se ejecutan con un HTML temporal *en memoria del navegador de CI*, del que se retira el modo preview, y **todos los hosts externos se interceptan como datos falsos**. Esas modificaciones nunca se suben al sitio ni al ZIP.

## Qué NO se modifica

`web-pasajero/app.js`, `route-engine.js`, `address-engine.js`, `eta-engine.js`, `booking-contracts.js`, `rpc-client.js`, `mapatrayectos/`, `conductor/`, `db/`, Supabase y producción. El CI coteja los blobs con la base canónica y bloquea cualquier cambio fuera de la lista estricta de archivos de QA.

## Condiciones para aceptar el hito

1. **Estática:** 3 servicios, un solo CSS de skin Quick Trip, CSS claro/oscuro, artefacto ZIP con checksum y manifiesto PWA.
2. **Visual:** capturas Chromium de Inicio y Tu Viaje a 393 y 360 CSS px; modo claro/oscuro; sin barra antigua, sin pantalla kilométrica ni permiso GPS abierto por defecto.
3. **Funcional simulado:** entrada de POI/dirección, estimación vial, precio sintético, historial ficticio, submit único y ruta fallida bloqueada.
4. **Resiliencia simulada:** Atrás, recarga, estado offline, reconexión, fallo HTTP 504, identificador estable al reintentar y doble clic.
5. **Caché:** Service Worker real en Chromium; diferencia de estilos entre precache y versión reciente, con servidor HTTP detenido.
6. **Sin backend real:** cero escrituras, tokens sintéticos, publicación y URLs de producción sin cambios.

**Nunca confundir:** pasar CI de Chromium **no equivale** a aprobación de fotografías originales ni de Samsung físico. `docs/quicktrip-canonical-contract.json` conserva esas aprobaciones como pendientes. No se integra ni despliega esta candidata automáticamente.

## Descarga e instalación QA

El workflow `.github/workflows/qa-quicktrip-webpwa-v86-canonical.yml` publica únicamente un **artefacto GitHub Actions descargable**, no Pages ni producción. Su ZIP contiene el mismo directorio `web-pasajero` para navegador y PWA.

Para validar instalación física en Samsung, hace falta **servido bajo HTTPS de un origen QA separado**, con su propio alcance y sin producción. Un ZIP o un archivo `file://` **no prueban** el Service Worker ni la instalación PWA. Un enlace CDN ajeno puede fallar por terceros y no debe presentarse como definitivo sin prueba completa de esa URL exacta.

## Reserva de seguridad

Si una prueba falla se conserva el artefacto de logs/capturas para diagnóstico, pero se bloquea la promoción. No borrar ramas, motores, ni respaldos.
