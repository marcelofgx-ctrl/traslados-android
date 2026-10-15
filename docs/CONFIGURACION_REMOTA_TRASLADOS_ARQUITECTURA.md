# Traslados — Configuración central remota, sin perder autonomía (decisión de arquitectura)

**Fecha:** 14/10/2026 (UYT). **Estado:** diseño propuesto y relevamiento comprobado; **NO implementado ni desplegado**. Ninguna APK, tabla, reserva ni servicio operativo cambia por este documento.

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
