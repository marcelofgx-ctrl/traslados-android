# Traslados — Configuración central remota, sin perder autonomía (decisión de arquitectura)

**Fecha:** 14/10/2026 (UYT). **Estado:** FASE 1 IMPLEMENTADA Y PUBLICADA — Supabase + Mapa R25.24. Las fases siguientes (Conductor independiente, admin visual, PWA remota) siguen pendientes. No se alteraron reservas, GPS, trayectos ni historial.

## Implementación efectiva, 14/10/2026 · 23:12 UYT

- **Supabase real** `zetaudvvutlouiqxopvg`: migración `public_app_config_read_only_v1` aplicada, función `public.public_app_config_v1()`, tabla privada `app_internal.public_config`; snapshot público SOLO enlaces y metadatos de release. Permisos `anon` permiten ejecutar la función, pero NO leer la tabla ni usar el esquema. Prueba `SET LOCAL ROLE anon` correcta. Config revisión **2**, catálogo Mapa **R25.24**, Cliente **v16**; el catálogo NO actualiza APK instaladas automáticamente.
- Reproducción SQL en `traslados-web/supabase/migrations/20261015_public_app_config_read_only_v1.sql`. No se usa nuevo plan pagado.
- **Android Mapa R25.24/code76**: `RemoteAppConfig.java` hace POST anónimo a la RPC pública en hilo secundario al abrir Mapa/centro rápido, con timeout 3,5+5 s, revalidación cada ~30 min, respuesta limitada a 16 KiB, validación esquema/revisión y dominio/ruta oficial HTTPS, último snapshot válido persistente y fallbacks locales R25.23. GPS y jornada no esperan este request. Se agregaron pruebas de `ShareLinkPolicy.resolve()`: al cambiar un enlace oficial conocido se pide reconfirmación; enlace personalizado se conserva sin sustitución.
- **APK RELEASE firmado y publicado:** [GitHub Actions #38669473570](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38669473570) SUCCESS, **14/10/2026 23:12 UYT**; archivo `web-pasajero/downloads/mapa-trayectos-r25.24.apk`, checksum SHA-256 `aa4accdf9dbb438ecaccedb4e991f3bd5aca29f9d527b726ff01375f32bb6092`, `MAPA-R25.24-SHA256SUMS.txt` en GitHub. R25.23 preservada como respaldo. El pipeline verificó firma, paquete, Java policy, integración Conductor y sonido.
- **Pendiente de comprobar:** respuesta de la RPC desde la app real instalada en Samsung, actualización remota tras publicar una futura Cliente v17, offline real y comportamiento de enlaces; QA visual/táctil, GPS/trayectos en movimiento no se repitieron para esta modificación de configuración. No declarar esos escenarios probados.
- Para publicar un nuevo cliente: certificar APK y URL **primero**, luego aumentar `revision`, actualizar `payload.links.client_apk_url` y `payload.releases.cliente` con versión y hash, conservar rollback anterior y probar acceso anónimo. No modificar enlaces personalizados ni escribir credenciales/PII en el catálogo.
- Supabase security linter puede mostrar el aviso genérico sobre una RPC pública `SECURITY DEFINER`; es intencional para este catálogo **sin parámetros y de solo lectura**, con tabla privada y `SET search_path=''`. Se deberán revisar los privilegios si se amplía el contrato; NO utilizar esta RPC para datos privados ni comandos.

## Motivo y evidencia verificable

- En Mapa R25.23, `mapatrayectos/src/main/java/uy/com/mapatrayectos/ShareLinkPolicy.java` fija en compilación la URL de Web Pasajeros y Cliente Premium v16; `QuickActionsMenu.java` usa esos valores y conserva valores personalizados en `SharedPreferences`. Cambiar la descarga oficial sigue obligando a migraciones o nueva APK.
- La PWA v71 tiene código estático desplegado por Pages; la APK Cliente Premium v16 carga esa web por WebView, por lo que puede recibir cambios web sin nueva APK (sujeto a caché/service worker y paridad).
- Supabase real `zetaudvvutlouiqxopvg` (consulta de solo lectura 14/10): existen `driver_settings`, `driver_availability_settings`, horarios y datos de viajes, **no** tabla de configuración general multiapp. `build_assets` no es catálogo público de versiones.
- No confundir cambio de contenido/configuración con cambio de código nativo: navegación, GPS, permisos, firma, UI estructural y servicios Android exigen build APK.

## Decisión técnica

Adoptar **configuración remota publicada, versionada y cacheada**, con **Supabase como fuente de verdad** para configuraciones y **Cloudflare Worker/API pública de solo lectura** como punto de distribución pequeño y cacheable. Alternativa igualmente válida: materializar JSON público en CDN/Pages a partir de Supabase; evitar consultar PostgreSQL en cada interacción. Web, Mapa, Conductor y Cliente consumen un mismo contrato con capacidades por aplicación.

Tres capas:
1. **Crítica y nativa (en APK)**: motor de navegación/heading-up, adquisición GPS y cálculos, SQLite/histórico local, burbuja overlay, notificaciones, permisos, autorización de acciones, firma y estructura visual. No depender de BBDD ni de Internet en cada ciclo.
2. **Operativa compartida (Supabase con permisos adecuados)**: horarios, franjas, reglas de reservas, cotización, servicios habilitados, canal de contacto, opciones de negocio y ajustes autorizados; validación del lado servidor imprescindible. No duplicar fuente de verdad.
3. **Presentación/configuración remota de baja frecuencia**: enlaces oficiales, página de instalación, versiones publicadas, mensajes y rótulos no críticos, banners/promociones, disponibilidad de módulos, feature flags compatibles, soporte; caché local y respaldo embebido.

## Contrato candidato `public_app_config_v1`

Ejemplo **ilustrativo**, no URL productiva nueva:

```json
{
  "schema_version": 1,
  "revision": 7,
  "published_at": "2026-10-14T22:00:00-03:00",
  "environment": "testing",
  "links": {
    "booking_web": "https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/",
    "client_install": "https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/"
  },
  "app_catalog": {
    "cliente": {"version": "16", "min_compatible": 15, "apk_url": null, "sha256": null},
    "mapa": {"version": "R25.23", "min_compatible": 25, "apk_url": null, "sha256": null}
  },
  "services": {"passengers": true, "parcel": false},
  "support": {"phone_public": null},
  "features": {"public_chat": false}
}
```

`apk_url`, `sha256` y teléfono se publicarían **únicamente cuando estén comprobados**. Los valores `null` requieren respaldo local o supresión del botón, no links inventados. En producción usar registro publicado separado de borradores de testing. Cada cliente ignora claves desconocidas; no se habilita una feature que su versión no soporte.

## Comportamiento Android / PWA

- Abrir con valores validados de la **última caché local**; si no existe, usar defaults conocidos empotrados. Pantalla y GPS **no esperan respuesta de red**.
- Revalidar por HTTP **en background** al abrir, al recuperar conectividad y solo después de TTL (p. ej. 30–60 minutos); opcional ETag/304. Nunca consultar por frame, cambio GPS, 15/25/30 s de ETA o apertura de menú.
- Timeout corto, validación del esquema/tipos, tamaños máximos, HTTPS y dominios de descargas permitidos; si falla, conservar **última configuración buena**, no borrar preferencias ni interrumpir jornada.
- Política de prioridad: personalización explícita del usuario prevalece sobre nuevos defaults oficiales, salvo consentimiento para restaurar. No sustituir URL personal silenciosamente.
- Cambios de `revision` se reflejan en los próximos accesos pertinentes; mostrar versión/configuración última sincronización en «Estado del sistema» y permitir «Actualizar configuración» manual con resultado honesto.
- El WebView/PWA aprovecha cambios web de Pages sin relanzar APK; separar actualizaciones de assets/service worker de datos remotos.
- Enlaces y avisos no críticos pueden utilizar un endpoint o página **estable** (`/instalar`, sin versionado en botón), que luego resuelva versión vigente; evita rotura de APK históricas. No publicar APK nueva hasta certificar firma y descarga.

## Seguridad, privacidad y administración

- Separar **catálogo público** estrictamente apto para invitados de valores privados de conductor, secretos, PIN, service_role, coordenadas o autenticación. Las aplicaciones cliente nunca reciben claves privilegiadas.
- Lectura pública solo del snapshot aprobado; escritores solo identidad administrativa verificada del lado servidor. Aplicar mínimo `GRANT`, RLS y pruebas de privilegios/anon/authenticated; no exponer tabla editable ni confiar en banderas para autorizar acciones: backend vuelve a comprobar.
- Distribuir solo datos declarativos permitidos; jamás código JavaScript ejecutable, SQL, URLs arbitrarias o parámetros capaces de desactivar salvaguardas de conducción.
- Versionar/auditar publicaciones, conservar rollback del último snapshot válido; cambiar punteros de releases de manera **atómica** después de verificar firma, hash y enlace.
- Tener en cuenta el cambio anunciado de Supabase para **30/10/2026**, que exige `GRANT` explícito para tablas nuevas expuestas mediante Data API. Referencia: https://supabase.com/changelog/45329-breaking-change-tables-not-exposed-to-data-and-graphql-api-automatically

## Entrega incremental sin romper app actual

1. **Inventario** de constantes compartidas en Mapa/Conductor/PWA/backend, clasificarlas en nativo crítico, negocio privado o público remoto.
2. **Piloto** de catálogo público solo para enlaces oficiales/instalación y versiones; esquema, autorización, API y cache, con pruebas de permisos, 304/TTL, offline, errores y rollback. No quitar hardcodes de respaldo.
3. **Mapa**: pasar `QuickActionsMenu`/enlaces al resolvedor dinámico cacheado; instalación sobre versión anterior conservando SQLite y preferencias. Exigir pruebas JVM, Android DEBUG/RELEASE firmada y Samsung detenido.
4. **Conductor y PWA**: consumidores del mismo snapshot; sin duplicar lógica ni requerir nueva APK por cambios web. Estudiar `app_catalog` para avisos de actualización, sin forzar durante un viaje.
5. **Operación y servicios**: centralizar textos, flags y horarios solo donde haya beneficio; parámetros de agenda/restricciones deben ser validados en backend.
6. Medir latencia, caché, tamaño, llamadas y uso antes/después; no asumir costo nulo. Evitar mezclar esta migración con cambios de navegación, GPS, historiales o bases históricas.

**Criterio de aceptación inicial:** al publicar y aprobar Cliente Premium v17, Mapa ya instalado comparte su nueva página/descarga oficial tras refresco de configuración, sin recompilar Mapa, conservando enlaces propios; sigue funcionando offline y con GPS idéntico.
