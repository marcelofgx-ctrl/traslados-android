# Quick Trip Live — QA física (preparación sin gastos)

**Aislado:** rama `qa/quicktrip-live-gps-integracion-20261027`. Ninguna reserva, PIN, sesión, APK ni tabla operativa. El Worker y D1 remotos no se crean desde un push GitHub ni desde una Preview Vercel.

## Paquetes previstos
- Pasajero QA: `/quicktrip-live-qa.html` en el proyecto Vercel `quicktrip-v86-qa-samsung-20261025`. Página autónoma, no altera `index.html`, `app.js`, `sw.js` ni el formulario productivo.
- Única APK Mapa QT con Conductor integrado, variante QA `uy.com.mapatrayectos.qa`; **no es una segunda APK Conductor ni APK Cliente**. Su manifest de QA sustituye todos los componentes de producción por una consola de prueba y su servicio de ubicación. Las clases históricas siguen compiladas pero sus componentes de producción no son ejecutables por el manifest QA. Verificar manifest MERGED antes de distribución.
- Backend: `qa/backend-worker` con D1 `qa_requests`, eventos y última posición QA. No hay integración con Supabase real. `src.mjs` y `tracking.mjs` ya tienen pruebas de contrato.

## Bloqueo por credenciales: indispensable antes de un remoto QA
No contamos con un acceso autenticado a Cloudflare desde esta conversación. **No introducir secretos en el chat, URLs con tokens ni capturas de credenciales.**

1. Acceder a [Cloudflare Dashboard](https://dash.cloudflare.com/) y verificar que el plan seleccionado sea Workers **Free**, sin complementos pagos ni tarjetas autorizadas para cargos.
2. Crear una base *nueva* D1 con nombre `quicktrip-live-qa-only`. No ejecutar SQL en Supabase ni en otra D1 existente.
3. Desplegar el Worker desde `qa/backend-worker/src.mjs` con binding `DB` a esa D1, tras correr `schema.sql` sobre **esa única base QA**.
4. Configurar **secretos en Cloudflare**, nunca en repositorio: `QA_PASSENGER_TOKEN` y `QA_DRIVER_TOKEN` distintos, generados localmente (mínimo 32 caracteres) y `QA_ALLOWED_ORIGIN` exactamente igual al origen HTTPS de la Preview Vercel. El token de pasajero es solo para el probador y el del conductor se introduce solamente en Mapa QT QA.
5. Probar desde navegador el `GET /health` del Worker QA. Debe responder `{ "ok": true, "qaOnly": true }`. No probar contra backend comercial.
6. Para uso remoto multiusuario se necesitan auth individual, cuota, bloqueo de abuso, limpieza/retención de GPS, y confirmación de que ninguna métrica de facturación está activada. **El piloto es de un solo conductor y pasajero de prueba.**

La plantilla `wrangler.example.jsonc` es deliberadamente inactiva. Habilitar recursos solo después de verificar el plan Free y el destino exacto. En Workers Free, si se exceden 100.000 solicitudes/día el servicio devuelve error 1027; mantener fail-closed en lugar de fall-through.

## Prueba de extremo a extremo
1. Abrir la página Vercel QA en Chrome con acceso autorizado al proyecto, ingresar URL HTTPS de Worker QA y token *pasajero de prueba*. Pulsar «Crear viaje QA»; se genera un UUID sintético, sin reserva real.
2. Instalar **solamente** el APK con paquete `uy.com.mapatrayectos.qa` desde el artefacto GitHub Actions validado; no desinstalar la APK Mapa QT actual.
3. En la consola «Mapa QT Live QA» introducir el mismo Worker QA, el UUID y el token *conductor de prueba*. Pulsar «Aceptar solicitud QA».
4. Pulsar «En camino» y luego «Activar compartir GPS». Conceder ubicación precisa y mantener la notificación de servicio de ubicación en primer plano.
5. El pasajero verá la posición del vehículo exclusivamente cuando el servidor acepte una muestra reciente (15 s de captura, precisión <=50m) y la ruta OSRM sea verificable. Sin ruta fiable, se oculta el mapa: **no se fabrica un conductor ni una ETA**.
6. Pulsar «En viaje» y luego «Finalizar viaje QA», o «Detener y revocar GPS». Confirmar que el automóvil desaparezca del pasajero al cerrar/cancelar y que no se pueda volver a transmitir sin nueva autorización.
7. Probar desconexión móvil, segundo plano y GPS apagado. Ante problemas los datos privados caducan 45 s después de su última publicación. No usar en circulación hasta QA física segura, idealmente con acompañante manejando la pantalla de pasajero.

## Criterios para declarar lista
- CI Android debe verificar `applicationId=uy.com.mapatrayectos.qa`, firma, manifest fusionado con **solo dos componentes QA**, y producir artefacto.
- Web Preview verificada mediante URL HTTPS real, con autenticación Vercel probada en los dispositivos.
- Cloudflare Free verificado por el usuario y ejecución real de preflight CORS desde origen Vercel, `POST` y `GET` autorizados.
- Pruebas Samsung real sin pérdidas de sesión y revocación confirmada.
- Sin esos hitos, **no se declara funcional el seguimiento Live remoto**.

La edición de archivos QA y la firma de esta variante Android están autorizadas; **main, Supabase real, producción y publicaciones comerciales permanecen fuera del alcance.**
