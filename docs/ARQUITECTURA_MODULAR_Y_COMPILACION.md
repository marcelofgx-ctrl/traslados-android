# Traslados — arquitectura modular, escalabilidad y compilaciones eficientes

**Decisión de arquitectura, 10/10/2026.**
**Estado:** fases iniciales implementadas y validadas en GitHub; el resto permanece planificado. No se considera terminada toda la modularización.

## Implementación real verificada — 10/10/2026

- **Web Pasajero v33:** `web-pasajero/address-engine.js` abstrae búsqueda IDE, filtrado de calle/puerta/departamento, resolución de coordenadas y geocodificación inversa GPS mediante `window.TrasladosAddress.score/find/resolve/reverse`; `app.js` dejó de consultar directamente IDE. `geo-search.js` continúa como módulo POI. HTML, service worker y suite de pruebas actualizados; PR [#24](https://github.com/marcelofgx-ctrl/traslados-android/pull/24), dos suites de CI SUCCESS y despliegue GitHub Pages v33 confirmado.
- **Android:** nuevo módulo Gradle **`:core-reservations`** de biblioteca Java pura, con código original `QuickReservationLogic.java`, incluido por `mapatrayectos` como `implementation project(':core-reservations')`. Cambió la localización del archivo, no su paquete ni la API pública. Test JVM existente y compilación integral de Mapa DEBUG pasaron en dos revisiones; PR [#25](https://github.com/marcelofgx-ctrl/traslados-android/pull/25) integrado. El flujo RELEASE firmó, verificó certificado y publicó el APK tras la integración (ver ejecución de Build Mapa).
- **Optimización:** `org.gradle.caching=true` en proyecto principal y Cliente Premium; generación `prepareEmbeddedConductor` elegible para caché al conservar entradas/salidas declaradas. Workflow `validate-modular-mapa.yml` verifica JVM + Android en PR; workflow Mapa detecta cambios en `conductor/src/**`, `core-reservations/**` y scripts compartidos. El verificador de APK Cliente ya detecta versión web en vez de exigir v28.
- **Medición inicial, sin prometer aceleración porcentual:** última ejecución de la etapa previa a la segunda reestructura registró aprox. **1,4 min** Mapa RELEASE, **1,5 min** Conductor RELEASE y **1,2 min** Cliente Premium RELEASE. Hay que medir builds sucesivos comparables para comprobar cuánto mejora el tiempo. No confundir duración total de CI con compilación incremental.
- **Lo pendiente:** aislar rutas/ETA, tarifa y reservas de `app.js`; unificar geobúsqueda con backend compartido si beneficia a todas las plataformas; convertir el embedding Java de Conductor en biblioteca Android real con equivalencia de recursos, notificaciones y firma; métricas longitudinales de CI. No introducir modificaciones grandes sin pruebas firmadas.


## Principios acordados

El objetivo de modularizar no es solamente compartir funcionalidades entre Web Pasajero, APK Cliente Premium, Conductor y Mapa Trayectos. Debe dar **orden, escalabilidad, mantenimiento sencillo, aislamiento de fallos, reutilización, tiempos de compilación/CI menores y cambios verificables**. Evitar microservicios artificiales, dependencias cíclicas y módulos diminutos. Modularización **medida**, no estética.

### Diagnóstico comprobado en el repositorio

- `settings.gradle` principal incluye `:cliente`, `:conductor`, `:mapatrayectos` y `:core-reservations`, mientras **Cliente Premium** está en un proyecto Gradle independiente `cliente-premium/` con `:app`.
- `mapatrayectos/build.gradle` tiene `prepareEmbeddedConductor`, que copia y transforma fuentes Java y recursos de `conductor/src/main` en carpetas generadas. Hace posible incluir el panel Conductor en Mapa Trayectos, pero es un acoplamiento delicado; la transformación por texto y la dependencia del árbol completo complican una evolución modular y la compilación incremental. **No eliminar esta tarea abruptamente**: afecta la compilación actual, recursos, alertas y el APK ya instalado.
- `web-pasajero/geo-search.js` ya aísla parte de la geobúsqueda, y `web-pasajero/address-engine.js` concentra IDE, puerta y GPS inverso; `app.js` aún concentra UI, ETA, reservas, ruta y sesión. **Cambiarlo a trozos pequeños manteniendo API y UI existentes.**
- Los workflows Android usan `gradle/actions/setup-gradle@v4` (caché de Gradle), pero muchos workflows están versionados y verificados contra cadenas específicas. `verificar-cliente-premium-publica.yml` ya determina la versión publicada de manera dinámica. Mantener este criterio en todas las comprobaciones.
- `build-mapa-trayectos.yml` y `build-conductor-release.yml` compilan y firman APKs con rutas independientes. Cambios compartidos en `conductor/` también deberían hacer ejecutar validación de Mapa, porque Mapa integra esas fuentes, y el workflow de Mapa ya escucha los cambios de `conductor/src/**` y `core-reservations/**`.

## Arquitectura objetivo (sin cambiar infraestructura por cambiarla)

```
aplicaciones/
  web-pasajero        (interfaz, PWA, modo entrega futura)
  cliente-premium     (shell Android/WebView, permisos Android)
  conductor           (app independiente de respaldo)
  mapatrayectos       (app operativa GPS+Conductor integrado)
servicios-clientes/
  geo-search          (normalización, calles, puertas, departamentos, POI)
  routes              (rutas, kilómetros, minutos, caché)
  eta                 (disponibilidad, movimiento reciente, llegada estimada)
  bookings            (reservas, estado, agenda, presupuestos)
  messaging           (notificaciones y comunicación)
  pricing             (reglas de tarifas y referencia de presupuesto)
backend/
  Supabase            (RPC, base, Edge Functions, RLS, migraciones)
  Cloudflare Workers  (servicio vial y endpoints públicos)
futuro/
  shipments           (mensajería/paquetería como dominio separado)
```

Es un **mapa lógico**, no un mandato de crear todas esas carpetas o desplegar servicios separados ya mismo. El backend puede conservar Supabase y Cloudflare, con interfaces estables y módulos funcionales internos. No trasladar credenciales, secretos o coordenadas sensibles a la web.

### Android / Gradle: división por dependencia real

1. **`:core:model`** — contratos y modelos puros Java, sin Android, para que compile rápido y sea fácilmente testeable.
2. **`:core:network`** — clientes HTTP, validación de respuestas, errores y DTO compartidos donde realmente corresponda.
3. **`:core-reservations` (IMPLEMENTADO)** — lógica independiente de estados, fechas y conflictos del panel de reservas. Presupuestos completos siguen pendientes de extraerse.
4. **`:core:location`** — datos GPS y políticas de frescura (no reemplaza `TrackingService` ni consentimiento sin pruebas).
5. **`:feature:driver` (eventual)** — refactor más delicado: sacar funcionalidad Conductor del mecanismo de copia de Java y compartir un módulo Android Library normal entre standalone y Mapa. Requiere tratar cuidadosamente namespaces, recursos, manifest, alertas, servicio GPS, burbuja y firma.
6. **Módulos de aplicación existentes** permanecen ensamblajes separados. Cliente Premium sigue siendo WebView; no necesita copiar módulos nativos que no usa.

Criterio: dependencia siempre de aplicación hacia módulo compartido; nunca `core` hacia `app`. Un módulo solo se crea cuando tiene límites claros, pruebas propias y al menos una razón real de aislamiento/reutilización.

### Compilación y CI

- **Medición inicial**: registrar duración total y por etapa en CI (checkout, SDK, dependencias Gradle, generación, JavaCompile, empaquetado, firma, APK, publicación), para distinguir cuello de botella real antes de optimizar.
- **Caché Gradle**: conservar `gradle/actions/setup-gradle`; ensayar `org.gradle.caching=true` y caché de build con pruebas de APK reproducible/firma. `org.gradle.parallel=true` únicamente si hay tareas independientes suficientes; no asumir ganancia automática.
- **Configuración incremental**: inspeccionar `prepareEmbeddedConductor` y limitar inputs/outputs/dependencias; a largo plazo sustituir transformaciones de fuentes en cada construcción por biblioteca real. Evitar tareas que reescriban miles de archivos sin cambios.
- **CI por impacto**: web JS/CSS solo ejecuta pruebas PWA (y Pages), no Gradle; un módulo Android dispara solo pruebas y aplicaciones dependientes. **Cambios en Conductor deben validar también Mapa**, porque comparte su código. Publicar APK release solo con firma/versión y controles apropiados.
- **Versiones y tests sin valores pegados**: derivar versión web desde `index.html`/manifest y versión APK desde metadatos Gradle, no mantener greps `v28` cuando la actual sea `v32`. Mantener seguridad de certificados, hash, integridad y firma.
- **Compilaciones locales**: `./gradlew :modulo:tasks`, `--build-cache`, pruebas de unidad aisladas y compilación incremental; medir antes y después. La fragmentación extrema perjudica rendimiento y legibilidad.
- **Trazabilidad**: evitar duplicación creciente de workflows con nombres de release históricos; consolidar los activos en pipelines estables con parámetros y `workflow_dispatch`, sin borrar workflows viejos hasta confirmar desuso.

## Orden de migración recomendado

**Fase 0 — Línea base y protección (sin riesgos):** inventario de dependencias y artefactos; registrar tiempos de compilación en 3–5 ejecuciones comparables; pruebas de firma, reserva, ETA/GPS, búsquedas, rutas y disponibilidad. Identificar jobs redundantes e invariantes obsoletos. No modificar permisos GPS ni API.
  
**Fase 1 — Beneficio rápido (bajo riesgo):** CI selectiva y limpieza de verificadores hardcoded; mejorar caché y registros de tiempos; separar geobúsqueda exacta de `app.js` con contrato externo idéntico y tests (Montevideo/Canelones, calle y número, departamentos, POIs); no tocar motor de rutas en esta etapa.

**Fase 2 — Bibliotecas puras (riesgo bajo-medio):** compartir modelos, estado de reservas y validaciones; habilitar pruebas unitarias y build incremental. No convertir shell Cliente Premium en APK pesada.

**Fase 3 — Integración Android limpia (riesgo mayor):** extraer Conductor a `:feature:driver` sin perder ninguna alerta, pantalla, servicio ni recurso. Mapa seguirá siendo controlador único del GPS/burbuja. Solo retirar `prepareEmbeddedConductor` al pasar compilación comparativa, pruebas de funcionamiento, actualizaciones de APK y equivalencia de firma.

**Fase 4 — Nuevos dominios:** mensajería y paquetería como servicios `shipments`, separados de reservas de pasajeros, reutilizando geo, rutas, ETA, notificaciones y tarifas configurables sin duplicar implementación.

## Criterios de aceptación

- Misma interfaz visual premium, experiencia y comportamiento; no introducir regresiones de reservas, ruta, disponibilidad y ETA.
- Mismo package ID, versión creciente y certificado de firma para actualizar las APK existentes; no publicar builds de prueba como releases operativas.
- Cambiar el módulo geográfico no recompila Android nativo salvo que cambie código/dependencias que lo consumen.
- Cambiar las reglas de tarifas no obliga a modificar geobúsqueda ni GPS.
- Cada módulo tiene responsables, interfaces/documentación y pruebas de regresión.
- Comparar métricas de tiempo/fiabilidad pre/post; **no prometer porcentajes de aceleración sin mediciones**.
