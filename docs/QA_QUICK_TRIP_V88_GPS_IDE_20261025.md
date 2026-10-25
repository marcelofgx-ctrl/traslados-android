# Quick Trip v88 — GPS y direcciones públicas IDE, solo QA
Fecha: 25-10-2026

## Síntoma reproducido por usuario en Samsung (v87)
En capturas físicas, luego de tocar GPS se selecciona `Ubicación actual (GPS)` pero no aparece el nombre de la calle como en versiones funcionales anteriores. La pantalla compacta mejoró; `Horario de recogida` y `Tus datos` aparecen bajo el botón principal de forma prevista.

## Causa técnica
`app.js` realiza `navigator.geolocation.getCurrentPosition()`, toma lat/lng y luego `TrasladosAddress.reverse(lat,lng)`. Este último método hace `fetch('https://direcciones.ide.uy/api/v1/geocode/reverse?...')`.
La v86/v87 tenía la CSP `connect-src 'none'`, por lo cual el navegador bloqueaba ese fetch, `app.js` atrapaba silenciosamente el error y usaba `Ubicación actual (GPS)` como texto de reserva. No se había eliminado ni cambiado el motor GPS.

## Corrección mínima v88
- Rama aislada `qa/quicktrip-v88-gps-ide-only-20261025`, desde v87 `922e771fbf3d1e8465eb178a2e89cc51030f88f2`.
- CSP `connect-src https://direcciones.ide.uy`, un único origen público gubernamental. No se autoriza Supabase real, Cloudflare de rutas, ni otro dominio para conexiones de datos. La dirección consultada depende de la cobertura y precisión de IDE; no se garantiza número de puerta exacto.
- Se ajusta exclusivamente el guard/leyenda QA, el identificador de caché v88, el manifiesto y el control de empaquetado.
- Intactos `app.js`, `address-engine.js`, `premium-map.js`, GPS, rutas, ETA, reservas y estilos compactos v87. `Solicitar viaje` sigue desactivado.
- El uso del botón GPS puede enviar latitud/longitud al servicio público IDE mediante HTTPS para recuperar una dirección. No enviar datos sensibles de clientes en QA.
- No modifica producción, ni una APK ni la base Supabase.

## Pruebas
Workflow `.github/workflows/qa-quicktrip-v88-gps-ide.yml` en GitHub, `40224093555`: PASS con 23 checks y 2 capturas a anchos Samsung 393 y 360.
Prueba Chromium crea ubicación simulada y respuesta IDE simulada, no consulta GPS ni direcciones personales. Comprueba CSP estricta, botón GPS original, retorno de calle ficticia, integridad de motor, CTA bloqueado, ausencia de otras redes y 0 llamadas a Supabase. El resultado NO verifica disponibilidad/CORS reales de la API IDE ni precisión de una dirección de casa.

## Despliegue
No desplegar v88 automáticamente: el proyecto Vercel QA tiene Previews automáticos deshabilitados. La Preview v87 seguirá mostrando la regresión hasta autorizar un único Preview v88 después de la revisión QA, verificar HTTPS y la API pública con dispositivo físico. Nunca pasar a producción ni fusionar sin aprobación expresa.
