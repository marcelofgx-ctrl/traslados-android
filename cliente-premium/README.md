# Traslados Cliente Premium 12.0 — Android

**Estado (10/10/2026):** nueva APK nativa compilada, firmada, con certificado **idéntico** al Cliente anterior R12 y **publicada mediante GitHub Pages auxiliar**, con SHA256 y entrega HTTP pública verificados. **No** existe aún una prueba física de instalación, login, GPS y envío de reserva en un Android real. La web oficial de Cloudflare sigue pendiente de desplegar sus cambios premium actuales por falta de `CLOUDFLARE_API_TOKEN` en GitHub Actions.

## Criterio: un producto, dos accesos

La APK **no duplica la interfaz** en XML/Java. La Activity nativa usa Android WebView y carga solo:

`https://traslados-web.marcelof-gx.workers.dev/`

Por lo tanto el diseño, las reservas, itinerarios con paradas, tarifas orientativas y la disponibilidad del conductor proceden **de la misma aplicación web oficial**. Cada mejora desplegada en Workers llega sin recompilar un duplicado de pantallas Android. La PWA GitHub Pages sigue como auxiliar técnica y puede mostrar diferencias. No hay redirecciones visibles a GitHub en el navegador de la APK.

La APK usa el identificador existente `uy.com.traslados.cliente`, versión **12.0-Premium** `versionCode 200`, y la **misma clave estable** que Cliente 11.5-R12. Una instalación encima de la anterior debería ser técnicamente admitida por Android, sujeto a compatibilidad del dispositivo. La cuenta y reservas reales permanecen en backend; puede ser necesario volver a iniciar sesión en el WebView porque los tokens anteriores estaban en almacenamiento nativo distinto.

## Qué ofrece la Activity

- WebView HTTPS fijo en el Worker canónico, sin JavaScript bridge expuesto ni secretos incluidos.
- Cookies de primera parte y almacenamiento web para uso autenticado y sesión.
- Permiso `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` solicitado mediante Android al pedir ubicación desde la web; no se elude bloqueo de superposiciones de Uber/Cabify/Mapa.
- Enlaces externos `https`, `geo`, `tel`, `mailto` y `whatsapp` delegados al sistema; llamadas y WhatsApp no se simulan.
- Selector de archivos con el mecanismo nativo de Android.
- Pantalla local premium de falta de red (ícono original de Traslados, reintentar y aviso de que no existen reservas offline).
- TLS estricto, no HTTP mixto, acceso a archivos locales bloqueado, sin cookies de terceros y sin copias de GPS del conductor.
- Ícono original de marca, ventana a pantalla Android con barras petróleo.

**No acreditado todavía:** push/notificaciones nativas, WebAuthn/biometría dentro de System WebView en todos los Android, ejecución de JS en segundo plano, pruebas de usabilidad en dispositivos físicos, modo offline de reservas. No anunciar como prestaciones hasta desarrollar y probar.

## Construcción verificable

`.github/workflows/build-cliente-premium-v12.yml` se ejecuta al cambiar `cliente-premium/**`, restaura Java 17, Android 35 y Gradle, corre `tests/check_premium_shell.py`, recupera la clave permanente **únicamente de GitHub Secrets**, compila Release Android y valida paquete + firma v2. Además descarga `SIGNING.txt` del Cliente R12 original (run `38021100002`) y compara **certificados SHA256 idénticos**; si no coinciden, aborta la publicación.

Luego publica el binario `web-pasajero/downloads/traslados-cliente-premium-v12.apk` y `PREMIUM-SHA256SUMS.txt` sin transformar el APK. El workflow `.github/workflows/verificar-cliente-premium-publica.yml` comprueba el archivo final por HTTP real y SHA256 y confirma que PWA v20 ofrece esa descarga.

**Pruebas relevantes:**
- RELEASE nativa [38073936665](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38073936665) **SUCCESS** con firma, ícono, package ID y checksum, artifact [Traslados-Cliente-Premium-v12-RELEASE](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38073936665).
- PWA v20 empaquetada [38074081842](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38074081842) **SUCCESS** y Pages [38074087540](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38074087540) **SUCCESS**.
- Publicación de APK probada por HTTP y checksum [38074274832](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38074274832) **SUCCESS**, 17.764 bytes para esa versión.

## Dependencias y operaciones

La API de reservas, PIN, distancia y presencia del conductor siguen en Workers y Supabase; esta Activity NO crea endpoints de segundo backend. Para ver los cambios recientes de `traslados-web/main` en la APK, es imprescindible completar **el despliegue canónico** Workers. El run 38074192866 tuvo compilación y TypeScript correctos, pero falló antes de publicar porque falta el secreto `CLOUDFLARE_API_TOKEN` en el repo web. También hay que configurar `CLOUDFLARE_ACCOUNT_ID` si no existe. Jamás compartir tokens en el chat.

**Prueba física obligatoria al estacionar:** instalar encima de R12, abrir, verificar título/ícono, iniciar sesión por PIN, probar GPS sin superposiciones, consultar trayecto/kilómetros, historial y **crear una reserva solo con consentimiento explícito**. Comprobar envío de enlaces WhatsApp y navegación externa. No probar mientras se conduce.

## Expansión comercial

La web fuente incorporó `ExpansionChannels`: empresas, alojamientos, pasajeros frecuentes y recomendaciones, cada uno con una consulta WhatsApp real y sin inventar convenios, tarifas ni cobertura. El plan comercial detallado se mantiene en `traslados-web/docs/PLAN_EXPANSION_TRASLADOS_2026.md`. Mantener un solo conductor hasta aprobar deliberadamente una futura arquitectura de varios prestadores.
