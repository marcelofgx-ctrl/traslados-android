# Quick Trip v89 — GPS intuitivo, UI Samsung, QA aislada

Fecha: 25 de octubre de 2026. Rama: `qa/quicktrip-v89-gps-ux-intuitivo-20261025`. Base inalterable: v88 `72f9eebac403026105e4f7ced55bbbe74a434d72`.

## Objetivo visual aprobado
En Tu viaje, reemplazar el botón «GPS» pequeño y perdido por una píldora elegante con icono de pin dorado y leyenda «Mi ubicación», ubicada bajo el campo de calle dentro de Origen (sin estrechar el buscador).

## Permiso y estados
- Permiso ya concedido: el botón ORIGINAL ejecuta su geolocalización y reverse IDE inmediatamente; no se pide permiso otra vez.
- Permiso pendiente: ayuda automáticamente desplegada, breve, con el botón nativo «Permitir ubicación»; no se pretende conceder permisos programáticamente.
- Permiso denegado: mensaje visible y pasos para habilitar Ubicación en el candado de Chrome / Ajustes de Android.
- GPS sin coordenadas: mensaje original disponible, reintentar o escribir dirección.
- Coordenadas válidas sin calle en IDE: indicar «GPS obtenido, pero no pudimos obtener la calle. Tocá Editar para ajustarla», sin inventar direcciones ni reemplazar coordenadas.
- Al elegir una calle, cerrar únicamente el aviso obsoleto «Solicitando ubicación al navegador…».

## Protección
Nuevo CSS y JS `quicktrip-qa-gps-ux-v89.*` enlazados desde `index.html` en QA; scripts originales de GPS, IDE, buscador, cálculo vial, ETA, reservas, mapas, sesiones y panel intactos. El JS v89 no ejecuta `fetch`, `getCurrentPosition` ni `permissions.query`; sólo observa estados del botón/ayuda del motor original. No cambia eventos ni reemplaza botones. Una sola llamada IDE pública autorizada por CSP heredada de v88 (`connect-src https://direcciones.ide.uy`); Supabase real y los demás orígenes bloqueados. El botón Solicitar viaje sigue desactivado. Nueva caché PWA `traslados-pwa-qa-gps-ux-v89-20261025`.

## Verificación y evidencia
`.github/workflows/qa-quicktrip-v89-gps-ux.yml` comprueba diffs contra la base, 13 motores y módulos visuales estables idénticos, política CSP de solo IDE, contrato de botones y empaquetado estático sin APK. Chromium simula teléfono Samsung de 393 y 360 píxeles, coordenadas IDE ficticias, permisos concedidos/bloqueados/pendientes, aísla toda red externa e inspecciona etiquetas, áreas del botón y capturas antes/después. No usa ubicación real ni toca Supabase.

## Restricciones de despliegue
NO fusionar, NO modificar producción, NO crear APK, NO tocar Supabase, NO publicar v89 automáticamente. Vercel QA debe mantener Previews automáticos deshabilitados. Un eventual Preview HTTPS v89 requiere revisión de las capturas y autorización posterior; los previews v86-v88 permanecen independientes.
