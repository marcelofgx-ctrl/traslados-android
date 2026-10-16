# Incidente y corrección del buscador IDE — 16/10/2026
PR #104 · PWA v75 · **NO afecta reserva, tarifa, ETA, Supabase ni Mapa**

## Síntoma real
Samsung 10:49 UYT: origen «Roque saenz peña», departamento Canelones, mensaje falso «Sin coincidencias en Canelones». Mismo texto duplicado en destino. El sitio exigía coordenadas válidas en la fase de **autocompletado**, y descartaba `CALLE` con `lat=0,lng=0`.

## Demostración con servidor oficial
[QA en vivo](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38911513309):
- `/api/v1/geocode/candidates?limit=30&q=Roque saenz peña`: HTTP 200, CORS *, calle real en Lagomar, Shangrilá, Parque Carrasco, San José de Carrasco (Canelones) y Montevideo. Tipo `CALLE` y lat/lng `0,0`, por diseño.
- `/api/v1/geocode/direcUnica?q=ROQUE SAENZ PEÑA, LAGOMAR, CANELONES`: HTTP 200 y coordenadas reales (punto **aproximado de la calle**, no número exacto). Responder únicamente después de seleccionar la opción oficial.
- No usar nunca lat/lng=0,0 como origen o destino. Mostrar advertencia para calle sin número: «Referencia aproximada» y sugerir altura o esquina.

## Cambios
1. `address-engine.js`: separación de candidatos de calle y puntos verificados. Consulta bajo demanda a `direcUnica`, con la localidad elegida. Rechaza geometrías inexistentes y alturas erróneas.
2. `app.js`: resultados de Canelones primero y luego «Otras ubicaciones en Uruguay» si aplica, con cambio automático de departamento al seleccionar. Si falla una fuente, no afirmar que la calle no existe. Control de respuestas atrasadas, botón de búsqueda que se reactiva.
3. `geo-search.js`: tiempo máximo de carga del catálogo, manteniendo los lugares conocidos si falla.
4. `styles.css`: subtítulos compactos, divisiones legibles y aviso de calle aproximada.
5. HTML `styles.css?v=75`, `app.js?v=75`, `address-engine.js?v=2`; service worker `traslados-cliente-pwa-v75-ide-addresses-20261016`. Se renueva la caché Samsung/PWA.
6. Regresión `roque-saenz-pena-autocomplete-smoke.cjs` prueba exactamente candidato 0,0 en Lagomar, Shangrilá y Montevideo, el filtrado preferente, resolución por IDE y prohibición de puntos ficticios. 41 pruebas PWA previas en CI, además del nuevo caso.

## Estado, validación y próxima prueba
Las verificaciones y despliegue se registran en PR #104. Tras publicación, abrir/actualizar web y probar «Roque saenz peña» en origen/destino con depto Canelones; elegir **Lagomar** o **Shangrilá**, comprobar posición de mapa y advertencia de aproximación; agregar calle/número para una recogida precisa. Validar también Montevideo, escritura con/sin tilde y GPS. No fusionar cambios de diseño premium pendientes por este hotfix.

**Pendiente transversal observado:** en la captura seguía apareciendo texto antiguo sobre disponibilidad/ETA. Si persiste tras v75, comprobar si se usa PWA instalada, navegador o APK Cliente empotrada; estas superficies tienen ciclos de actualización diferentes. No atribuir automáticamente eso al motor IDE.
