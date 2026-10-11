# Traslados PWA v46 — fidelidad al boceto de «Tu recorrido» (11/10/2026)

## Motivo
El usuario aprobó una composición visual de origen y destino con **campo largo + lupa dorada + GPS junto al campo**. Sin embargo, la v45 publicada mantuvo el botón «Buscar» de texto ancho y «Usar ubicación actual» debajo. La discrepancia era de estructura DOM, no solo color o márgenes.

## Cambio implementado sobre el repositorio existente
- En `picker()` (`web-pasajero/app.js`) ambas acciones se crean en **la misma fila de origen**: dirección flexible, lupa y posición actual. El destino tiene dirección y lupa. No se genera una fila extra para GPS.
- Botones de 47 px de ancho (44 px en teléfonos estrechos); textos originales conservados semánticamente y `aria-label` explícitos, iconos SVG CSS discretos; la búsqueda sigue llamando `lookup()` y la ubicación sigue llamando `useCurrent()` con preparación y permisos Android actuales.
- Selector abreviado visualmente a **«Otros»**, manteniendo la etiqueta accesible «Otros departamentos de Uruguay» y las 17 opciones; al seleccionar un departamento no principal, el valor seleccionado se ve destacado.
- Espaciado y conector vertical Origen–Destino más compactos, sin alterar texturas, color petróleo, oro, bordes suaves, distribución de tres pasos ni presupuesto referencial.
- Teclado Enter del buscador dispara la misma `lookup()` existente, cancelando el retardo pendiente.
- `index.html` PWA v46 para JS/CSS, `sw.js` cache `traslados-cliente-pwa-v46`; Cliente Premium **v15 sigue siendo la WebView existente**, sin recompilar release.

## Compatibilidad y seguridad
Ninguna modificación de `geo-search.js`, `address-engine.js`, `route-engine.js`, `eta-engine.js`, disponibilidad, GPS/consentimiento, Supabase, `pickup-eta`, Mapa Trayectos R24.8, programación, identidad, API de reserva, tarifa, paradas, aceptación ni notificaciones.

El compás flotante que aparece en capturas de Samsung pertenece a una superposición externa al formulario; el CSS de la página no puede mover una burbuja de otra aplicación. No simular que se corrigió ese elemento.

## Validación
- Nuevo test `web-pasajero/tests/route-mockup-v46-smoke.cjs` exige que ambas acciones estén realmente en la **misma fila** del origen, con iconos y navegación del teclado.
- Conservación de todos los tests anteriores y las tres verificaciones CI para PWA, formulario y Cliente WebView.
- **Pendiente hasta verificar:** tests CI, integración PR, Pages.
- Prueba física Samsung pendiente: comprobar campo/lupa/GPS en una fila sin desbordes con tamaños de fuente grande, departamento, búsqueda, selección, GPS permitido/denegado, plegado, consulta urgente y reservas autorizadas. No generar reservas reales para probar estética.
