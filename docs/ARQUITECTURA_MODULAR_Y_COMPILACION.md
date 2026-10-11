# Traslados — arquitectura modular, escalabilidad y compilaciones eficientes

**Decisión de arquitectura, 10/10/2026.**
**Estado:** plan y línea de trabajo; ninguna fase de refactorización se declara implementada por este documento.

## Principios acordados

El objetivo de modularizar no es solamente compartir funcionalidades entre Web Pasajero, APK Cliente Premium, Conductor y Mapa Trayectos. Debe dar **orden, escalabilidad, mantenimiento sencillo, aislamiento de fallos, reutilización, tiempos de compilación/CI menores y cambios verificables**. Evitar microservicios artificiales, dependencias cíclicas y módulos diminutos. Modularización **medida**, no estética.

### Diagnóstico comprobado en el repositorio

- `settings.gradle` principal incluye `:cliente`, `:conductor`, `:mapatrayectos`, mientras **Cliente Premium** está en un proyecto Gradle independiente `cliente-premium/` con `:app`.
- `mapatrayectos/build.gradle` tiene `prepareEmbeddedConductor`, que copia y transforma fuentes Java y recursos de `conductor/src/main` en carpetas generadas. Hace posible incluir el panel Conductor en Mapa Trayectos, pero es un acoplamiento delicado; la transformación por texto y la dependencia del árbol completo complican una evolución modular y la compilación incremental. **No eliminar esta tarea abruptamente**: afecta la compilación actual, recursos, alertas y el APK ya instalado.
- `web-pasajero/geo-search.js` ya aísla parte de la geobúsqueda, pero `web-pasajero/app.js` todavía concentra dirección exacta IDE, interfaces, ETA, reservas, ruta, sesión y flujos de usuario. **Cambiarlo a trozos pequeños manteniendo API y UI existentes.**
- Los workflows Android usan `gradle/actions/setup-gradle@v4` (caché de Gradle), pero muchos workflows están versionados y verificados contra cadenas específicas. `verificar-cliente-premium-publica.yml` aún contiene una comprobación rígida `app.js?v=28`, mientras la PWA actual es v32: falso fallo de integración posible. Corregir esta deriva en su propia etapa de higiene de CI.
- `build-mapa-trayectos.yml` y `build-conductor-release.yml` compilan y firman APKs con rutas independientes. Cambios compartidos en `conductor/` también deberían hacer ejecutar validación de Mapa, porque Mapa integra esas fuentes, aunque hoy el filtro del workflow de Mapa escucha principalmente `mapatrayectos/**`.

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
3. **`:core:reservations`** — lógica comprobada de agenda, estados y presupuestos independientes de vistas.
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
