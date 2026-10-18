# PICKUP-RECALC-001 — Recalcular llegada al cambiar el origen de recogida

**18/10/2026 · PR funcional de revisión apilado sobre PICKUP-ORIGIN-STALE-001 (#140), no publicado.**

## Evidencia y causa raíz
Captura Samsung 13:29: punto de recogida Aeropuerto Internacional de Carrasco y destino World Trade Center seleccionados, pero tarjeta «Llegada todavía sin estimar» / «No pudimos verificar la ruta del conductor hasta tu origen». En Supabase logs agregados 13:19–13:34 UYT, `pickup-eta` recibió 4 solicitudes con HTTP 200; el cuerpo puede ser `available:false, reason:route_unavailable`. HTTP 200 no prueba ruta válida. En el backend, la Edge requiere una ruta vial verificada desde el Worker Cloudflare (ORS o caché vial autorizada): si falla, responde `route_unavailable`, sin publicar coordenadas del conductor ni inventar kilómetros.

El código ya claveaba ETA por `record.origin` y tenía pruebas de respuestas anteriores tardías, pero no invalidaba *sincrónicamente* toda la presentación de recogida al cambiar las coordenadas, ni ofrecía un reintento visible y sujeto a cuota.

## Contrato conservado y cambio delimitado
- Recogida **conductor → origen** depende **únicamente del origen de recogida**; cambiar el destino solo recalcula **origen → destino** (motor de viaje), no la ETA de recogida.
- `recognizePickupOriginChange` compara las coordenadas de origen del formulario; al cambiar las coordenadas invalida de inmediato ETA anterior, error, antigüedad visual y respuesta asíncrona de disponibilidad, y muestra estado «Nuevo origen · calculando llegada».
- El flujo que ya funciona `renderRouteSummary → refreshPickupPresence` dispara la ETA del nuevo punto sin esperar el intervalo de la anterior. Reutiliza hasta 8 s de disponibilidad válida. No omite limitaciones de backend ni reintentos con token de visitante.
- Si la Edge responde `route_unavailable`, mostrar el estado honesto sin cifras. `Reintentar llegada` solo habilitado fuera del intervalo de 25/45 s, sin petición en curso y fuera del backoff `rate_limited`. La PWA también sigue reintentando automáticamente.
- Mantener la edición real de direcciones y paradas, estado de reservas, GPS del conductor, consentimiento, tarifas y privacidad intactos. El mapa del *viaje* y el resumen premium siguen regulados por UX-RECORRIDO-001 y son otra tarea; no dibujar una ruta ficticia.

## G3/G4/G5
- Prueba `pickup-origin-recalc-smoke.cjs`: proveedor sin ruta, error visible, nuevo origen invalida estado viejo y envía otra solicitud con la misma disponibilidad reciente, destino diferente no altera el cálculo, resultado tardío descartado, no hay bypass de cadencia.
- CI Web review y CI de empaquetado de revisión deben aprobar sobre el mismo SHA; ZIP debe incluir los archivos y checksums.
- Prueba física Samsung y verificación de respuesta real ORS/Worker siguen obligatorias: si el proveedor no puede verificar ruta, el usuario continuará viendo «Sin estimación» con reintento, no una ETA inventada. **No publicar automáticamente** ni tocar Supabase operativo.

**Dependencia:** cadena #119 → #122 → #123 → #125 → #140 → este PR. No recompilar APK nativa.
