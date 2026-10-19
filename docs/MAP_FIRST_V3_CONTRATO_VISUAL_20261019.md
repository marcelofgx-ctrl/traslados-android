# MAP-FIRST V3 — Contrato de diseño consolidado y QA
Fecha: 19/10/2026. **Solo versión de pruebas**; sin envío de reservas.
Base: main con PWA publicada estable separada y previa Map-First V2 en `web-pasajero/pruebas/map-first/`.

## Objetivo
Convertir «Tu viaje» en un producto con jerarquía móvil inequívoca:
mapa vial real en el primer nivel; precio, km y minutos contiguos;
ETA de recogida separada; opciones y privacidad progresivas.
El conductor no se representa como marcador ficticio. Se conservan
los cálculos, direcciones alternativas, paradas, límites y horarios.

## Arquitectura preservada
Únicos archivos de visualización de V3: `map-first-v3.css` y `map-first-v3.js`.
Se referencian desde `index.html` solo de la copia de ensayo,
con parámetro `?mapfirst=1`. Los elementos se **reubican**, no se
duplican, manteniendo listeners, IDs y estados. No modificar backend,
Supabase, APK Mapa, web oficial, servicio de mapas, precio ni GPS.
La copia de ensayo conserva meta `traslados-preview` y bloqueo de
los botones de envío. No incluye service worker.

## Cuatro superficies de la decisión
| Pantalla | Información visible | Bajo demanda |
| --- | --- | --- |
| Recorrido | Origen/destino, mapa verificado, banda de precio/km/min, ETA/estado de recogida, Continuar | Paradas y editor de origen/destino |
| Cuándo | Horarios 2 columnas y botón Continuar, ETA compacta si hay dato | Pasajeros y detalles, hora exacta |
| Tus datos | Cuenta autenticada con nombre/teléfono, Continuar | Indicaciones, email y consentimiento local |
| Confirmar | Origen/paradas/destino, precio final conocido, acto de confirmación | Descripción auxiliar |

## Sistema visual
Petróleo `#061b21` / `#0d2c32`, champagne `#efd194`,
verde de estado `#70d3a1`, texto `#f4f1e8`.
Textura de líneas de opacidad mínima, gradientes moderados,
bordes tenues e íconos de la UI existente; tipografía numérica fuerte.
Botones con blanco/chapagne y contraste AA cuando sea posible.
No animaciones obligatorias; respetar `prefers-reduced-motion`.

## Contrato de presentación
1. No más de un precio principal visible por etapa.
2. Mapa **por delante** del panel de ETA y de los editores de paradas.
3. `#stop-wrap` colapsado de inicio aunque existan 2 o más paradas.
4. `Continuar` en flujo (no `position:sticky/fixed`) con margen
   inferior y hueco derecho respecto al globito Android.
5. ETA del conductor no sustituye duración del traslado;
   sin GPS verificable se mantienen mensajes «sin estimar».
6. Cuenta existente evita pedir nuevamente nombre/teléfono;
   opciones de email, notas y consentimiento permanecen accesibles.
7. Sin nuevas llamadas RPC, sin promesas de GPS en vivo y sin limpiar datos.

## QA obligado
- Comparar visualmente V2/V3 en Samsung 360, 393 y 412 px.
- Estado con 0 y 2 paradas; abrir/cerrar editor sin perder direcciones.
- Ruta vial verificada y proveedor caído: no tarifa o geometría ficticia.
- Probar volver desde Tu viaje a Tu reserva: no romper inputs anteriores.
- Probar Cuándo / Tus datos con y sin sesión y Confirmar.
- Confirmar cero superposición con globito, barra inferior o teclado.
- No merge de #145/#147 ni publicación general antes de aprobación.
- La revisión de código o test de patrones no sustituye QA visual real.

Estado inicial: V3 candidata; validación funcional y revisión Samsung pendientes.
