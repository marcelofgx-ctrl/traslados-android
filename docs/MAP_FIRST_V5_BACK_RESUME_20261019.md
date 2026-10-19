# V5 — Corrección de navegación Atrás y recuperación de estimaciones

**Fecha:** 19/10/2026. **Ámbito:** solo vista experimental `web-pasajero/pruebas/map-first/`. No APK Mapa, Supabase, PWA pública ni reservas.

## Reproducción real (Samsung)
1. En «Tu viaje», elegir origen y destino; se ve ruta con precio, km y minutos.
2. Pulsar Atrás de Android. La PWA sale al navegador en vez de regresar a Inicio o al paso anterior.
3. Volver a la aplicación: origen/destino permanecen, pero ruta/ETA puede quedar «Sin estimación».
4. Solo cerrar y abrir a veces recupera los cálculos.

## Causas observadas en código
- `shiftView` y `premiumJourney.open` actualizaban DOM, pero no registraban pasos en `history.pushState`; el botón Android navegaba fuera.
- `renderRouteSummary()` se disparaba principalmente al modificar direcciones; faltaba un camino fiable para reintentar la ruta tras `pageshow`, volver a primer plano o reabrir Tu viaje.
- La capa de recogida tiene cuotas y espera por rate-limit independientes; la recuperación debe respetar ese límite.

## Contrato de la corrección
- Estado de navegación de prueba: `history.state.trasladosMapFirstNav` con `view` y `stage`, sin direcciones, GPS ni datos personales.
- `popstate`: reconstruye el estado visible usando `shiftView` y `premiumJourney.open` existentes; no añade entradas mientras restaura.
- Flujo esperado Android Atrás: Confirmar → Tus datos → Cuándo → Recorrido → Inicio. Desde Inicio, Android maneja cierre normal.
- Regresar de segundo plano, del BFCache o reabrir Tu viaje: si falta una ruta **verificada** y hay puntos válidos, invalidar solo esa clave y volver a llamar al motor existente. No fabricar importes ni trayectos.
- No cancelar ni duplicar solicitudes viales todavía en vuelo; límite de reintento breve para eventos repetidos.
- También volver a consultar la tarjeta de llegada del conductor, dejando que el motor actual haga respetar las cadencias y el backoff de cuota.
- Conservar cuenta, dirección, paradas, horario y datos de la pantalla. No enviar reservas automáticamente.
- Mismo scope de service worker de ensayo; cache invalidada a V5 y recurso JS de navegación incluido.

## QA requerida
`web-pasajero/pruebas/map-first/tests/map-first-v5-back-resume.cjs` simula los estados de Android Atrás, `popstate`, `pageshow`/BFCache y la vuelta desde segundo plano. Ejecutar test en CI y verificar en Samsung:
1. Ruta confirmada: Atrás desde Recorrido vuelve a Inicio; reentrar conserva puntos y la ruta.
2. Entrar a Cuándo, Tus datos y Confirmar; Retroceder por etapas con botón de Android.
3. Simular proveedor vial no disponible → Atrás → reentrar: debe ofrecer intento automático o mostrar motivo, nunca un importe anterior.
4. Reanudar después de cambiar de aplicación sin cerrar Chrome: actualizar cuando corresponde, respetando cadencias.
5. Abrir Cuenta, volver y revisar que no se haya borrado historial ni credenciales.
6. En prueba, botón «Enviar» sigue bloqueado; no publicar en producción hasta QA física.

**Estado:** corregido en rama de ensayo, pendiente de CI/despliegue y prueba Samsung.