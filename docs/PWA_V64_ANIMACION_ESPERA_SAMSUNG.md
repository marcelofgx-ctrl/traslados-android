# Traslados · PWA v64 · Espera visual de llegada en Samsung

**12/10/2026 · PUBLICADA.** Continuidad sobre PWA v63, sin modificar las aplicaciones nativas firmadas ni los contratos de disponibilidad y ETA.

**Despliegue verificado:** PR #65 fusionado a main `cfb85e07`; [Pages SUCCESS](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38277644999), [PWA CI SUCCESS](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38277646051), revisión visual automatizada y validación Android WebView v15 correctas. **QA físico Samsung pendiente**.

## Evidencia y causa

Captura real de Samsung: «Conductor disponible para recogerte · Actualizando la llegada…» con distancia/ETA aún no obtenidos y sin animación. El problema visual es reproducible por inspección: la capa CSS v60 ocultaba `pickup-route-animation` en `data-phase="waiting"`, aunque el módulo JavaScript usaba precisamente esa fase durante la espera/intervalo entre consultas.

La disponibilidad positiva es un resultado independiente de la obtención de la distancia hasta el origen. La captura NO demuestra por sí sola un fallo de GPS o de rutas.

## Mejora implementada

- Mantener el recorrido ilustrativo mientras la tarjeta pasa por **checking**, **routing**, **waiting** y **ready**. Cuando se confirma disponibilidad se usa un vehículo SVG original de color **verde menta** con desplazamiento suave de lado a lado sobre ruta discontinua verde.
- Animación lenta y discreta (3,7 segundos por tramo, alternando sentidos), sin destellos ni falsas simulaciones de progreso real. Respeta `prefers-reduced-motion`, mostrando el vehículo inmóvil.
- En la espera y después de obtener resultados, etiqueta breve **«Animación ilustrativa · No es seguimiento en vivo»**. Cuando se está solicitando ETA la etiqueta funcional indica «Calculando llegada a tu origen…».
- Sin origen elegido, disponibilidad negativa o error de comprobación, no se muestra el vehículo de recogida en movimiento. Las estimaciones anteriores se ocultan en estados negativos y nunca se inventan kilómetros/minutos.
- El refresco de disponibilidad sigue siendo **15 s** y la estimación del motor **25–45 s** de acuerdo con cadencia y backoff existentes. No se adelantan consultas ni se alteran cuotas.

## Cambios de versión y límites

- PWA **v64**: `app.js?v=64`, `styles.css?v=64`, service worker `traslados-cliente-pwa-v64`.
- HTML conserva formulario, parámetros, navegación y acciones existentes; solo reemplaza el rombo de la animación por el pequeño SVG de automóvil.
- `service-home.js?v=3` y portada orbital PWA v63 siguen iguales.
- Mapa **R25.0**, Conductor **11.5-R5**, Cliente Premium **v15 WebView**, backend Supabase, GPS y tarifas intactos.
- Prueba dedicada `pickup-motion-v64-smoke.cjs` agregada a CI web y revisión. Los tests históricos mantienen sus controles y se actualizan para los parámetros de caché.

## QA en Samsung a realizar luego de CI y despliegue

1. En Chrome y WebView, elegir **Ahora** o **En 10 min** con origen seleccionado. Si disponibilidad confirmada, ver vehículo **verde** recorriendo la línea incluso cuando la llegada diga «Actualizando…».
2. Una vez obtenidos kilómetros/minutos reales, comprobar que continúe la animación sin sustituir ni tapar las métricas.
3. Apagar la red: el aviso de error/no confirmación debe ocultar la animación y limpiar estimaciones obsoletas en cuanto el estado operativo deje de ser confirmado.
4. Probar sin origen y modo **Programar**; no debe anunciar distancia conductor→origen ni exhibir falso seguimiento.
5. Activar **Quitar animaciones** en Android y revisar que el ícono quede quieto, manteniendo legibles los textos.
6. Confirmar que no existe desplazamiento lateral, que el servicio de reserva sigue enviando solo ante pulsación real y que Cliente WebView carga la PWA nueva sin reinstalar.

**No se crean reservas reales ni se modifican datos de producción durante las pruebas automáticas.** Un flujo extremo a extremo requiere prueba física autorizada.

## Reversión

Revertir la PR de v64 y esperar el ciclo Pages/service worker; no reinstalar ni firmar nuevas APK. Los servicios originales quedan intactos.
