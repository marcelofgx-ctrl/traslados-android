# Traslados Cliente / Web — Mosaico modular incremental
16/10/2026 · [PR #101](https://github.com/marcelofgx-ctrl/traslados-android/pull/101) DRAFT · Base: PR #99

## Decisión
El pasajero tiene una PWA instalable y una APK Cliente Premium, **ambas con la misma web canónica y las mismas reglas de negocio del servidor**. Mosaico significa módulos de responsabilidad única bajo una sola experiencia visual, NO dividirlo en aplicaciones instalables, duplicar pantallas o inventar microservicios.

No se alteran el diseño petróleo/champagne/dorado, la sesión del usuario, Supabase, Mapa ni las URL públicas.

## Estado arquitectónico inspeccionado
- El runtime web ya tiene 18 scripts autónomos inicializados con `defer`: direcciones/IDE, rutas, ETA, contratos de reserva, RPC, pasos, seguimiento, servicios, instalación y presentación premium.
- `web-pasajero/app.js` sigue siendo un coordinador grande (no cortar sin mantener UI/regresiones).
- La APK Cliente Premium es un contenedor Android WebView que consume GitHub Pages en producción y empaqueta un snapshot aislado del PR99 en QA.
- Antiguo empaquetador PR99 copiaba **todos** los archivos web y reescribía `MainActivity.java`/Gradle con reemplazos de cadenas: fallos de anclas, tareas Gradle invalidadas, más CI por cambios de tests y downloads.
- Antes, cuatro workflows de la rama PR99 podían compilar/ejecutar pruebas de Android por cada cambio web, incluso sin cambios nativos. Otros workflows históricos permanecen en el repo; no borrarlos sin auditar versionado/usuarios.

## Cambios aplicados
```
web-pasajero/                        PWA canónica, componentes ES existentes
  {address,route,eta,...}.js          lógica dividida por responsabilidad
  app.js                              coordinador de interacción (pendiente subdivisión)
  sw.js                               offline; no guarda envíos de reserva
scripts/
  verify_passenger_mosaic.py          paridad módulos HTML ↔ cache PWA + RPCs
  passenger_assets.py                 assets content-addressed, limpieza incremental
  prepare_premium_preview.py          deriva variantes JS/CSS/HTML, NO reescribe Java
  tests/test_passenger_assets.py      prueba copia/limpieza/reuso
cliente-premium/
  app/src/main/.../MainActivity.java  WebView + permisos + navegación
  app/src/main/.../MosaicPreviewGateway.java
                                     política nativa separada de assets/API
  app/build.gradle                    flags de modo de preview
```

### CI y economía de compilación
1. `qa-cliente-mosaico-fast.yml` ejecuta 44 tests Node, comprobación de dependencias web-PWA, Python y seguridad **sin Gradle, emulador ni llamadas de red a Supabase**.
2. `build-cliente-mosaico-v27.yml` compila **una** APK firmada si cambian los assets runtime/nativo. No se dispara por PDFs, descargas históricas ni simples correcciones de tests. Cancela builds superados en la misma rama.
3. `qa-cliente-mosaico-webview.yml` inicia **un emulador Android** para ambas variantes aislada/operativa; solo ante cambios del contenedor Java y tests instrumentados, o despacho manual.
4. Compara SHA256 de **todos los assets** contra el contenido real del APK, firma contra el certificado anterior, `applicationId=uy.com.traslados.cliente`, `versionCode=215`.
5. La tarifa se lee de Supabase, no se fija a UYU 40/km en los tests de ruta/precio.

### Medición real
- [Build PR99 anterior](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38886772542): Gradle RELEASE **21 s** (un run).
- [Build Cliente Mosaico v27](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38894470100): Gradle RELEASE **14 s** (un run), con `--build-cache`. Son muestras distintas y no constituyen benchmark repetido de aceleración.
- Segundo empaquetado en el mismo runner: **32 de 32 assets reusados**, 0 copiados y ninguna variación de mtime ni byte de HTML/JS/CSS, Gradle, Java o manifest.
- Binario de prueba v27/code215: [descarga](https://raw.githubusercontent.com/marcelofgx-ctrl/traslados-android/feature/traslados-cliente-mosaico-20261016/preview-artifacts/traslados-cliente-mosaico-v27-prueba.apk). No es la APK pública.
- [44 tests rápidos + assets](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38894318250): PASS.

## Evolución mosaico siguiente (sin cambios funcionales prematuros)
1. Extraer de `app.js` coordinadores de reserva/fare y actualización de estado a módulos sin DOM global, mediante un contrato/adaptador (nunca duplicar llamadas RPC ni modificar flujos).
2. Aislar `auth/session` (una sola identidad por solicitud; acceso a reservas sin pérdida de viajes) y centralizar persistencia por usuario.
3. Consolidar ETA/rutas/tarifa en un único objeto de recorrido confirmado; invalidar a la vez kilómetros/minutos/precio/ETA cuando cambia origen, destino o parada.
4. Implementar un orquestador de solicitudes idempotentes, fuera de la vista, con PIN y confirmación conductor solo en servidor. No reintentar automáticamente POST ambiguos.
5. Centralizar `app-version/public-config` en Supabase para enlaces de descarga actuales sin acoplar WebView a versiones de APK; respetar cache busting y fallback PWA → APK.
6. Regresiones visuales diferenciales del diseño premium y pruebas reales en Samsung: teclado, GPS, retorno desde Maps, permisos, chat y sesión, pantalla abierta/cerrada.
7. Medir 5 builds consecutivos (frío/caliente) y Android PSS, consumo CPU/red antes/después. No afirmar ahorro de batería sin instrumentación.

## Publicación
El PR #101 está en borrador. No se modifica `main`, las versiones públicas de la web, ni los datos del backend. Solo fusionar después de pruebas E2E Android/Web y revisión visual. El PR #100 de Mapa es independiente.
