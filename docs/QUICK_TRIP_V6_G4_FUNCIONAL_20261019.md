# Quick Trip V6 G4 — Primer corte funcional opt-in (19/10/2026)

## Contrato
Este corte es la **primera versión funcional de Quick Trip V6**, sin motores duplicados y sin nueva APK. El usuario aprobó dejar atrás el laboratorio visual y comenzar a probar un producto real.

- Entrada para prueba, al publicarse Pages: `https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/?ui=v6`.
- Entrada oficial sin parámetro: `/web-pasajero/` sigue con su interfaz actual.
- Laboratorio ilustrativo antiguo: `/web-pasajero/pruebas/visual-v6/`, aún no realiza reservas.
- **PWA y web usan el mismo archivo** `index.html` y las mismas cuentas/sesiones Supabase por origen. La modalidad `?ui=v6` activa únicamente un tema y composición de presentación, sin bifurcar lógica.
- Android Mapa Trayectos queda idéntico y NO se genera APK Cliente.

## Cambios realizados
1. `web-pasajero/quicktrip-functional-v6.js`: se carga después de `app.js` y retorna inmediatamente si el parámetro `ui` no vale `v6`. Cambia sólo textos/estética del encabezado, círculos, progreso y tema persistente; **no agrega listeners a los botones de reserva**.
2. `web-pasajero/quicktrip-functional-v6.css`: reglas acotadas a `body.qt-functional-v6`, claro y oscuro, portada con fotografía Montevideo y emblemas SVG existentes, 124 px principal y 104 px restantes. Botón de tema visible, CTA y navegación inferior.
3. `index.html`: sólo carga el script opt-in después de la PWA oficial. No se modifica la configuración ni se reemplazan módulos.
4. Laboratorio visual: mismo ajuste de tamaño y encabezado compacto para preservar la referencia estética.

## Comportamiento funcional verdadero
- «Tu Viaje» usa `TrasladosServiceHome.onSelect('now')` → `enterService('now')` → `premiumJourney.activate()` → edición de ruta, horario, pasajero y solicitud inmediata real.
- «Tu Viaje Programado» usa `onSelect('schedule')` → flujo funcional de reserva programada existente (fecha y hora), sin simular viaje instantáneo ni crear motor nuevo.
- «Tu Paquete» sigue en **próximamente**, sin RPC de encomiendas.
- Autocompletado OSM/IDE, selecciones de paradas, GPS con permiso, ruta vial, km/min, tarifa parametrizada, ETA, cuenta, historial, seguimiento y chat pertenecen todos a los **archivos existentes de producción**.
- El cambio claro/oscuro es puramente CSS. No vuelve a calcular la ruta ni inventa valores.
- Aviso explícito en la portada y antes de la solicitud: **las solicitudes enviadas desde este modo usan el servicio real**.

## Limitaciones para la siguiente iteración
- La apariencia del programado se adapta al estilo V6, pero todavía usa la pantalla funcional `view-reservar`; por ahora no se fuerza un recorrido de cuatro etapas que pudiera comprometer fecha, horarios o identidad.
- El mapa, la ETA y el precio deben verificarse sobre un origen/destino reales en Samsung, con conexiones efectivas. Los tests estáticos no confirman disponibilidad externa de motores.
- Revalidar en Samsung los modos claro y oscuro, sin superposición de WhatsApp/Uber/Cabify, teclado visible, formulario de fecha y hora, historial, navegación Back, conexión lenta y errores del backend.
- Las pruebas de flujos que envían realmente solicitudes requieren coordinación y no se ejecutan automáticamente contra producción.
- Evitar tocar `app.js`, `route-engine.js`, `eta-engine.js`, `service-home.js`, `booking-contracts.js`, `premium-journey.js` salvo regresión probada.

## G0–G6
- G0: motores existentes preservados y exclusión de la APK y Supabase.
- G1: PR acotado y revisión automática.
- G2: tests de contrato opt-in + pruebas originales.
- G3: CSS y navegación sin duplicación de datos.
- G4: modo funcional real bajo `?ui=v6` con advertencia de solicitudes reales.
- G5: pendiente de QA Samsung por el usuario (no confundir con tests estáticos).
- G6: promoción al diseño predeterminado únicamente tras la comprobación real en Samsung, manteniendo opción clásico/V6 si así se decide.
