# PWA v45 — pulido premium del recorrido (11/10/2026)

## Contexto y objetivo
La captura Samsung mostraba un formulario de origen/destino demasiado alto, un «Buscar» de anchura similar a la dirección, departamentos pesados y una flecha aislada. Se conserva **la identidad visual aprobada petróleo/grafito, dorado suave, textura, tipografía y acordeón de tres pasos**, junto con los motores y servicios vigentes. El objetivo es un bloque más atractivo, compacto, cómodo al tacto y visualmente coherente. **No se reconstruye desde cero ni se cambian contratos de backend.**

## Intervenciones
- **Cabecera:** texto introductor más breve, jerarquía y espaciado afinados. Sigue explícita la obligación de confirmación del conductor.
- **Campos:** fila móvil en cuadrícula real de columna flexible para la dirección y acción Buscar de anchura controlada (28–30 %, 93 px en <=355 px); evita que el botón se expanda a mitad de pantalla. Bordes suaves, icono de lupa CSS/SVG vectorial, interacciones de presión sobrias.
- **Departamentos:** chips Montevideo/Canelones preservados, selección dorada más fina; los otros 17 departamentos continúan en `select` **nativo** (teclado y accesibilidad Samsung intactos), visualmente secundario, sin proveedores de pago.
- **Recorrido:** un solo bloque Origen–Destino, títulos A/B con microindicadores distintos y conectividad visual por una línea/flecha sutil en vez de espacio vacío. Menores márgenes sin sacrificar legibilidad.
- **Mi ubicación:** botón secundario compacto, en el mismo flujo del origen y con la **misma lógica de solicitud de permiso Android y consentimiento**. Sin pedir ubicación automáticamente.
- **Resultados:** botones de dirección más compactos con jerarquía tipo/nombre, flecha de selección y respuesta táctil sutil. Solo quedan seleccionadas ubicaciones obtenidas por los motores existentes; no se inventan sugerencias, distancias ni ETA.
- **Estados:** aspecto de ubicación seleccionada más definido, manteniendo «Editar» y sin perder datos al plegar.
- **Accesibilidad:** etiquetas de campos específicas por origen/destino, `enterKeyHint=search`, `aria-controls` hacia sugerencias, conector decorativo `aria-hidden`, foco visible y `prefers-reduced-motion`.
- **Versionado:** `web-pasajero/index.html` app/CSS v45; SW `traslados-cliente-pwa-v45` (caché coherente); la APK Cliente Premium v15 abre **la misma PWA mediante WebView**, sin alterar firma o paquete.

## Invariantes
- Mapa Trayectos R24.8, GPS opt-in, privacidad, jornada, demanda adaptativa 15/25/30s, presencia de conductor 15s, ETA 25/45s y Supabase Edge pickup-eta v5: **sin cambios**.
- Buscadores `geo-search.js` e `address-engine.js`, geocodificación IDE, POI, departamentos, paradas, motor ROAD, presupuesto orientativo, autenticación, formularios, botones de envío, confirmación y estado de reservas: **sin cambios funcionales**.
- «Ahora»/«En 10 min» conservan el camino honesto introducido en v44; petición urgente interna queda en issue #38, no se declara existente.
- La burbuja superpuesta de otras aplicaciones (Uber, Cabify o Mapa Trayectos) **no puede ser movida desde la PWA**; no se simula que la web pueda controlarla. Probar en Android sin superposiciones al solicitar el permiso de ubicación.
- No generar reservas ficticias en producción para validar el diseño.

## Validación
- Smoke específico `web-pasajero/tests/route-premium-v45-smoke.cjs` y regresiones existentes, más compilación de compatibilidad Cliente v15.
- CI y Pages pendientes hasta consultar el resultado real del PR.
- QA físico aún pendiente: Samsung 320–430 dp, zoom/fuente grande, resultados IDE y POI, GPS con y sin permiso, selección y edición A/B, plegado, entrada de paradas, consulta urgente, confirmación y actualización de WebView.
