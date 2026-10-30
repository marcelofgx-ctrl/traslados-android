# Quick Trip P1.4 · Recuperación de estimaciones sin perder el diseño premium

**Fecha:** 30/10/2026. **Base P1.3:** `0d0654d54923cd0683ac844123986bd29c648ee2`.
**Rama:** `qa/quicktrip-p14-repair-estimates-preserve-design-20261030`.

## Diagnóstico confirmado

1. El endpoint público de rutas `/api/testing-road` en el Preview QA existente devolvió HTTP 200 con ruta vial verificada, kilómetros, duración y precio para una pareja de coordenadas de prueba dentro de Uruguay. Esto **no** prueba que toda consulta del usuario llegue correctamente o que el servicio siempre responda.
2. Un test Chrome 390×844 seleccionó origen y destino por el selector original, simuló por HTTP las cuatro respuestas legítimas (disponibilidad, recogida, ruta vial y tarifa), y verificó que P1.3 presentara los **cinco valores reales del controlador** sin errores JavaScript. Esto descarta una desconexión permanente del DOM, **pero no es una prueba del GPS real ni de Supabase real**.
3. Error funcional reproducible: `quicktrip-clean.css` oculta deliberadamente `#route-overview` en el rediseño; el reintento original del motor vial `#route-overview .route-retry` se alojaba dentro de esa sección. Si la consulta vial fallaba, el pasajero quedaba con kilómetros, duración y tarifa «Sin estimación» sin poder reintentar desde la pantalla premium. El motor no quedaba permanentemente invalidado; faltaba recuperación accesible.
4. En un error de disponibilidad pública, el panel `#pickup-presence` informa el error y el controlador vuelve a consultar según su cadencia, pero el pasajero no disponía de un reintento visible de **disponibilidad**. La recuperación de ETA propia sí existe en `#pickup-eta-retry` y su cooldown, y no se duplica.

## Implementación P1.4

- La hoja P1.3 aprobada **permanece idéntica**: `quicktrip-p13-approved.css` no cambia. `quicktrip-p14-estimate-recovery.css` añade únicamente estilos de error y recuperación, nunca presenta un elemento adicional en estado normal.
- `quicktrip-clean.js` muestra `#qtc-route-recovery` **solo** cuando la ruta vial ha fallado realmente o cuando ya está verificada pero la tarifa sigue sin confirmar. El botón llama al **mismo** `#route-overview .route-retry` original, o, para la tarifa, invalida su caché usando el mismo `roadEngine` y ejecuta `renderRouteSummary()`. No se añaden motores, fórmulas ni estimaciones.
- En reconexión, regreso a primer plano o restauración desde historial del navegador, un reintento automático de ruta **solo si el error sigue presente** con separación mínima de 90 segundos; no reintentar si ruta vigente o sin origen/destino.
- La tarjeta «Tu conductor» muestra `#qtc-presence-retry` solo cuando la consulta de disponibilidad de `publicDriverAvailability` devolvió resultado no verificable; llama a `refreshPickupPresence(true)` existente. Para problemas exclusivamente de ETA, sigue el reintento nativo y su límite de 25/45 segundos.
- `sw.js` nuevo namespace de caché, referencias CSS/JS versionadas en `index.html` para evitar que el Samsung mezcle componentes antiguos con los nuevos. No se altera `app.js`, los dos motores, Edge Function, SQL ni la APK.

## Evidencias y condiciones de aceptación

- Browser E2E con `originPicker.persist` y `destPicker.persist`: ruta → 18,8 km / 26 min / $752; disponibilidad → conectada; recogida → 1,8 km / Hasta 5 min, **todos valores de fixtures, no condiciones reales**.
- Fallo intencional de ruta, ver error y retry, respuesta correcta posterior en los cinco campos; fallo de disponibilidad, ofrecer «Comprobar conductor», recuperación sin duplicar GPS ni reservar.
- Reejecutar suite de pure route engine, fixed fare, ETA, cadence, movement warning y QA P1.3 con screenshot 390×844; conservar pines verdes/coral, distribución y CTA.
- Verificar diff de `main` y ausencia de cambios de motores/Android/Supabase.
- Preview QA **solo** si todo CI está verde. Prueba física pendiente en Samsung y con conductor autorizado para declarar ETA real.

## No se puede afirmar todavía

No tenemos una captura de consola/red del Samsung durante la falla ni una lectura autorizada de los recursos privados reales; por ello no se atribuye el error a la Edge Function, al permiso GPS, a CORS o a una cuota concreta. Si los reintentos nuevos fallan, el diagnóstico debe capturar **código de motivo no sensible** y disponibilidad visible, sin credenciales, coordenadas privadas ni cambios al backend.

**Prohibido:** producción, merge a main, cambios de rutas/ETA, Supabase real, reservas nuevas, APK Android, publicación pública comercial.
