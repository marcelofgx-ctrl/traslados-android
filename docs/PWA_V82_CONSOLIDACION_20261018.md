# PWA v82 — Consolidación publicada (18/10/2026)

## Resultado verificado en GitHub

**La consolidación ya fue terminada y fusionada. No repetirla.** La cadena anterior de trabajo era **#119 → #122 → #123 → #125 → #140 → #141**; se integró como un único cambio a través del [PR #142](https://github.com/marcelofgx-ctrl/traslados-android/pull/142).

- **Main desplegado:** `a26e81298d8ac7f10070abb6ffdee1305ef1e3cf` (18/10/2026).
- **Candidato consolidado y verificado:** `fa4a0b246c24ad897b444f28b0e0ff081b6fa63b`.
- **PR G1 alcance:** [#39149069924](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/39149069924) — SUCCESS.
- **PR pruebas PWA:** [#39149049975](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/39149049975) — SUCCESS.
- **PR paquete:** [#39149050162](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/39149050162) — SUCCESS.
- **Main paquete:** [#39149300095](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/39149300095) — SUCCESS.
- **Pages build + deploy:** [#39149300406](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/39149300406) — SUCCESS (deploy publicado para el mismo commit).
- **URL pasajero:** https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/
- **Versión:** HTML `app.js?v=82`; SW `traslados-pwa-v82-consolidado-20261018`.

La información de Pages está verificada en GitHub Actions. El entorno de ejecución no pudo resolver directamente el dominio `github.io` para una lectura HTTP separada. La actualización efectiva de una **PWA ya instalada** debe confirmarse en Samsung.

## Cambios incluidos y protegidos

- **#119:** fases de seguimiento real del pasajero.
- **#122 + #125 (DIR-001):** búsqueda, alturas, fallback con precisión y validación.
- **#123 (STATUS-001):** estados fijos y heredados sin volver a presupuestos ambiguos.
- **#140 (PICKUP-ORIGIN-STALE-001):** después de GPS voluntario, detectar posible desplazamiento >250 m y pedir confirmación; nunca mover reserva automáticamente ni registrar GPS privado.
- **#141 (PICKUP-RECALC-001):** cambio de origen invalida ETA/error anteriores y solicita nueva estimación; cambiar destino no reinicia ETA conductor→origen; si el proveedor no da ruta validada, reintento sujeto a cadencia y cuotas, sin inventar minutos/km.

La v82 ajustó el versionado de `app.js`, CSS, módulos modificados y Service Worker. Las antiguas aserciones literales de v81 se actualizaron preservando sus reglas de negocio y las pruebas de eliminación segura de caché vieja. Los seis PR anteriores quedaron cerrados por reemplazo, no son seis merges separados.

## QA y pendientes abiertos — NO marcar resueltos todavía

1. **Samsung real:** comprobar que navegador y PWA instalada carguen v82; si hay caché vieja, actualizar con la propia gestión del navegador, **sin desinstalar ni borrar datos**.
2. **Origen de recogida:** cambiar A→B, comprobar que desaparezca la ETA anterior y se consulte nuevo origen; cambiar solo destino y verificar que no altere la ETA hacia recogida.
3. **GPS pasajero:** al usar GPS explícitamente y trasladarse >250 m con formulario visible, debe pedir «Actualizar origen» o «Mantener origen». No mover dirección de forma automática, no vigilar fondo ni viajes confirmados.
4. **Recorrido vial:** verificar la respuesta real de proveedor Cloudflare/ORS para Aeropuerto de Carrasco → World Trade Center. `HTTP 200` en Edge **no significa** que exista geometría. Si `route_unavailable`, mostrarlo sin ruta/precio falsos.
5. **Reservas:** probar el camino real de solicitud, recepción y confirmación con cuidado de no duplicar solicitudes. Los datos operativos/históricos no se purgan.
6. **UX-RECORRIDO-001:** **no implementado** en v82. Sigue el trabajo separado: dibujar ruta vial verificada en mapa y mostrar precio orientativo, km y duración bajo el mapa, sin duplicar ETA en tarjetas.

**Arquitectura oficial invariable:** web pública, una sola web/PWA del pasajero y una única APK nativa Mapa Trayectos que integra Conductor. No generar APK Cliente ni Conductor. Mapa R25.34 Play/Stop es QA de Android, independiente de esta publicación.

## Continuidad y fuente de verdad

GitHub `main`, PR #142, CI de Actions, despliegue de Pages y Supabase privado `project_ops.work_memory` (`handoff:20261018`) son los registros a contrastar; no confiar en resúmenes históricos que mencionen PWA v71/v81 o PR todavía abiertos. No crear una segunda metodología: aplicar siempre G0–G6.

**Próxima tarea segura:** QA Samsung + proveedor vial, después PR mínimo separado para **UX-RECORRIDO-001**. No reabrir ni duplicar el PR #142.
