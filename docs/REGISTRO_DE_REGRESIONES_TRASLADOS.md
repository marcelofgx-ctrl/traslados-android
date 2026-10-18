# REGISTRO DE REGRESIONES — Traslados

**Fecha de apertura:** 18/10/2026. **Regla obligatoria:** no cerrar un defecto por CI sintético, ni marcar publicación sin verificar el deploy y dispositivo. Registrar todos los cambios de estado con PR/SHA.
**Norma:** [METODOLOGIA_PERMANENTE_TRASLADOS.md](METODOLOGIA_PERMANENTE_TRASLADOS.md).

| ID / gravedad | Síntoma y causa | Evidencia histórica | Estado y cierre requerido |
|---|---|---|---|
| DIR-001 / P0 | «Arenal Grande 2274, Montevideo» termina sin resultados. `find()` conservó fallback `direcUnica`, pero el `findAcross()` creado para sugerir otros departamentos **no lo reutilizó**; la UI utiliza `findAcross()` y el smoke prueba `find()`. | Corrección previa PWA v29 (commits `2a83162dfa08`, `85222632a68f`, `324a4616fcca`); v33 extracción `fcf2a779662d`; regresión introducida por cambio v75 `298f4ffe8374`. Reporte Samsung 18/10. | **ABIERTO.** Consolidar pipeline único y test que utilice **el picker real** con fallback y falsos positivos. Sin geocodificación real de esa altura verificada aún. Rama preparada `fix/pwa-direcciones-alturas-instalacion-20261018`, sin corrección implementada. |
| STATUS-001 / P0 | En precio fijo el historial presenta «Presupuesto aceptado», aunque el contrato actual es aceptación directa del viaje. Etiquetas derivadas por caminos independientes (`trip-board.js`, `app.js`, conductor Android). | Capturas Samsung 17–18/10; código `app.js::tripTimeline`, `trip-board.js::metadata`, `conductor/MainActivity.java`. | **ABIERTO.** Centralizar semántica por modalidad; test de pantalla/timeline en precio fijo y conservar flujo de presupuesto legado solo donde siga siendo requerido. |
| GPS-001 / P0 | Vista ampliada del viaje usa mapa contratado independiente del seguimiento privado que utiliza la tarjeta. Confunde seguimiento real con mapa estático. | Código `app.js::renderPremiumTracking` main vs `reservation-live.js`; PR [#119](https://github.com/marcelofgx-ctrl/traslados-android/pull/119) con propuesta. | **EN QA / NO PUBLICADO** al último control; confirmar branch/rebase, CI, Pages y Samsung antes de cerrar. |
| MON-001 / P0 | Mapa podía dejar de recibir peticiones al detenerse el monitor o faltar sesión integrada; monitor y refresco manejaban casos diferentes. | Reportes Samsung 17/10; PR [#120](https://github.com/marcelofgx-ctrl/traslados-android/pull/120), R25.29 release de revisión. | **EN QA física**; revisar Mapa abierta/minimizada, notificaciones, reconexión y una alerta por reserva. No garantizar recepción 24/7 sin push. |
| AUTH-001 / P1 | PIN solicitado repetidamente al conductor principal dentro de Mapa por almacenamiento/autorización no consolidados. | PR [#121](https://github.com/marcelofgx-ctrl/traslados-android/pull/121), R25.30 release firmado de revisión. | **EN QA física**; verificar vínculo único, sesión recuperada, actualización sin pérdida y seguridad Android Keystore. |
| CACHE-001 / P1 | PWA instalada muestra etiqueta antigua (v78) mientras `main` contiene recursos PWA v81. Puede haber caché/despliegue divergentes. | Captura Samsung 17–18/10; main `sw.js` v81 al momento de auditar. | **POR DIAGNOSTICAR**; comparar recursos realmente servidos, manifest, SW, caché instalada, Pages y SHA antes de atribuir la causa. |

## Procedimiento al registrar un caso nuevo

- Crear ID y estado sin borrar entradas cerradas.
- Anotar el recorrido exacto del usuario; versión instalada, commit/PR, servicios involucrados y datos de repro.
- Enfocar pruebas en **la API/función usada por esa pantalla**, no en un helper alternativo.
- Documentar el commit que introdujo la regresión solo después de cotejar historial.
- Cerrar únicamente después de CI contra SHA exacto + deploy/firmas verificados + resultado de QA real cuando corresponde.
- Una rama de revisión y un release prerelease **no son producción**.

Este registro es una línea base **parcial**. Los estados se revisan contra GitHub en vivo en cada sesión; no convertir estos datos históricos en afirmaciones automáticas de actualidad.
