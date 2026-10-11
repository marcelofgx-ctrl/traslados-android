# PWA v53 — Ajuste mobile sin desplazamiento horizontal

Se mantiene el diseño petróleo/dorado de Traslados, sin alterar presupuesto, mensajería, reserva, estados ni ETA.

## Motivo
El tablero Mis traslados tenía filtros `Próximos / Por responder / Historial / Todos` montados en una tira flex con `overflow:auto`. En móviles chicos creaba un desplazamiento lateral corto e innecesario. Algunos elementos con ancho mínimo también podían empujar el contenido fuera del viewport.

## Cambios
- `html,body`: clip de desbordamiento lateral usando `overflow-x:clip` (no crea un contenedor de scroll que afecte la navegación sticky).
- Navegación principal: columnas `minmax(0,1fr)`, permitiendo que los títulos se ajusten al ancho.
- «Mis traslados»: filtros **2×2** en móvil hasta 520 px, sin scroll horizontal. En escritorio se conserva la disposición vigente.
- Flex/grids de tarjetas, estados y acciones: se permite contraer el contenido y repartir botones sin ensanchar el viewport.
- Pantallas muy pequeñas: filtros Día/Mes y Estado en una columna.
- Pie de página: enlaces en filas si no entran.
- Se conserva desplazamiento **vertical**, áreas táctiles, cabecera fija, navegación, diálogos, sonido/vibración y chats.

## CI / QA
Nuevo smoke `web-pasajero/tests/mobile-overflow-v53-smoke.cjs` en workflows PWA y revisión. HTML/CSS/Service Worker v53 para invalidar caché. Se requiere inspección visual con navegador real en ancho 320, 360, 390 y 412 px, tanto Chrome Android como WebView de Cliente Premium, y confirmar que `document.documentElement.scrollWidth <= document.documentElement.clientWidth`; las pruebas estáticas de CI **no** sustituyen la inspección en Samsung.
