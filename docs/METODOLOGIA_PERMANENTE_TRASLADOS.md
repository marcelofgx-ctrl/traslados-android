# METODOLOGÍA PERMANENTE DE TRABAJO — Traslados / Mapa Trayectos

**Adoptada:** 18/10/2026 · **Vigencia:** entre sesiones, ramas, PR, versiones y herramientas.
**Carácter:** norma del proyecto. No sustituir por instrucciones improvisadas de una sesión.
**Fuente de verdad:** código y despliegues verificables en GitHub/Supabase; este documento define el MÉTODO, no certifica qué versión está publicada.

## Índice privado de memoria operativa (v1 · 18/10/2026)

Además de GitHub y los documentos de esta metodología, existe una memoria breve de trabajo en **Supabase Traslados** (`project_ops.work_memory` y `project_ops.work_memory_events`). **Se consulta explícitamente mediante herramienta administrativa al empezar cada sesión**, y se actualiza tras verificar hechos contra GitHub/CI/servicios. No es memoria automática del modelo ni fuente de verdad de qué está publicado.

- **Leer solo 30 registros prioritarios** (`active/open/in_qa/blocked/implemented`) para identificar dependencias, decisiones y defectos; el documento técnico y consulta están en [Memoria operativa privada — traslados-web](https://github.com/marcelofgx-ctrl/traslados-web/blob/main/docs/MEMORIA_OPERATIVA_TRASLADOS_20261018.md) (fusionada desde PR #20).
- Verificar primero el **HEAD de main y los PR vivos**. Los registros pueden estar desactualizados y no certifican QA Samsung.
- Actualizar una entrada con `revision` esperada; el diario `work_memory_events` conserva historial. **Jamás borrar**, no guardar secretos, ubicaciones privadas ni imágenes del paquete.
- Esquema fuera de la Data API; **no conectar PWA ni APK** a esta tabla. Si Supabase no está accesible, continuar desde GitHub, sin declarar sincronización automática.

**Regla de precedencia:** código / despliegue / DB de negocio verificados > registros de memoria > resúmenes históricos. Es un mecanismo de localización y continuidad, no una autorización para publicar.

## 0. Principios innegociables

1. **No rehacer.** Recuperar primero el contexto vigente y el trabajo existente, incluidos PR abiertos y documentación de decisiones. No recrear una APK, pantalla, módulo o servicio si ya existe.
2. **Producto único, tres superficies:** web pública; web/PWA pasajeros (misma experiencia en navegador e instalada); **única APK Android Mapa Trayectos con Conductor integrado**. No construir APK Cliente ni APK Conductor independientes. Backend de Traslados Supabase `zetaudvvutlouiqxopvg`; nunca modificar otro proyecto (Oak & Ember).
3. **Un propietario por responsabilidad de negocio:** una sola implementación de búsqueda/geocodificación, estado de viaje, contrato de reservas, precio, disponibilidad pública, GPS privado, identidad y alertas. Los adaptadores de interfaz pueden diferir, **las reglas no**. No confundir fuentes diferenciadas por seguridad (GPS privado vs disponibilidad pública).
4. **Corregir causa raíz y proteger el comportamiento previo.** Cada defecto recurrente exige análisis de cuándo apareció, commit que lo introdujo, prueba de regresión reproducible y comprobación en la superficie real. No aceptar parches locales que dejan otro flujo sin cobertura.
5. **No borrar ni resetear datos** (viajes, reservas, GPS, PIN, cuentas, configuración, históricos) para hacer pasar pruebas. Toda migración debe ser conservadora, reversible y explícitamente autorizada. No crear solicitudes de prueba en producción como efecto secundario de CI.
6. **No declarar «pronto», «publicado» o «probado» sin evidencia exacta.** Sintaxis, compilación, QA sintética, firma, release, deploy y QA física Samsung son hitos DIFERENTES. No extrapolar de uno al otro.

## 1. Inicio obligatorio de CADA sesión (antes de editar)

1. Consultar `README.md` → este documento → `docs/REANUDAR_TRASLADOS.md` → `docs/ESTADO_AUTOMATICO_TRASLADOS.md` → PR abiertos relevantes → versiones/release/Actions actuales. Los archivos automáticos o cronológicos pueden estar atrasados: contrastar con GitHub en vivo.
2. Confirmar **fecha/hora UYT**, commit SHA de `main`, ramas que contienen cambios no fusionados, despliegue Pages (PWA), release firmado (Mapa), y backend/Supabase. Informar lo que está publicado frente a lo que es review, sin confundir PR con despliegue.
3. Explicitar las **tres superficies** y las dependencias; identificar qué artefacto y qué función modifica la tarea. Recuperar requisitos previos antes de solicitar al usuario que los repita.
4. Revisar el `REGISTRO_DE_REGRESIONES_TRASLADOS.md` y la lista de bloqueantes; no iniciar una funcionalidad nueva P1/P2 si hay un bloqueo P0 de reservas, recepción o seguridad.
5. Elegir una base vigente y declarar por qué; nunca basarse automáticamente en un PR antiguo archivado ni cambiar la rama principal a mitad de un trabajo.

## 2. Ciclo de desarrollo por cambio (NO saltar puertas)

**G0 — Investigación.** Reproducir y ubicar el código real ejecutado desde la interfaz; identificar regla canónica, contratos y posibles duplicaciones. Comparar commits para hallar regresión. Registrar causa comprobada frente a hipótesis.

**G1 — Contrato e impacto.** Escribir criterio de aceptación medible, invariantes que deben seguir funcionando, módulos afectados y recorrido de extremo a extremo. Si se modifica una RPC o tabla, revisar RLS/permisos, privacidad, migraciones y clientes antiguos.

**G2 — Rama pequeña y cambios mínimos.** Partir de la base verificada, una responsabilidad por PR. No duplicar lógica existente. Si hay adaptadores `find`/`findAcross`, delegan en un núcleo compartido. No mezclar rediseño visual, reestructura y cambios de negocio si complica QA.

**G3 — Pruebas contra el camino REAL.** Agregar prueba que FALLA antes de corregir cuando sea posible y PASA después; ejecutarla junto con regresiones anteriores. La prueba debe usar la función/interfaz que ejecuta el pasajero o Mapa, no solo helpers parecidos. Abarcar errores, reintentos, doble solicitud, offline, bloqueo/red, cambio de cuenta y datos persistentes.

**G4 — Compilación y QA técnica.** Comprobar SHA exacto de la rama, tests, análisis estático, integración y firma/package de la APK; web HTML+JS+SW cache bust y pruebas visuales móviles. Un CI verde únicamente es evidencia de ESA ejecución y commit. Registrar fallos y no esconderlos.

**G5 — QA de integración.** Recorrido: pasajero busca dirección → origen/destino/paradas → ruta/km/precio → envía → Supabase registra → Mapa recibe/alerta → conductor acepta/rechaza → ETA real a recogida → recogida → viaje GPS a destino → finalización → historial/chat. Separar pruebas sintéticas de pruebas reales; para pruebas reales, usar entorno aislado o autorización explícita y conservar datos.

**G6 — Promoción gradual y entrega.** Con QA aprobada, revisar PR, fusionar según dependencias, desplegar Pages/Worker/DB o firmar APK según corresponda. Comprobar la URL final, revisión de PWA y Service Worker en **dispositivo**, firma APK compatible, rollback y **último commit**. Reportar enlaces verificados a web pública, PWA y única APK Mapa. Si falta QA física, decir «REVIEW — no validado en Samsung» y no publicar como estable.

**Puerta de bloqueo:** si una etapa falla, la tarea queda pendiente con causa, prueba y próxima acción. No avanzar a G6 por presión de versión.

## 3. Contratos únicos y cobertura mínima

| Dominio | Fuente canónica (o destino a consolidar) | Regresión indispensable |
|---|---|---|
| Búsqueda de direcciones | `web-pasajero/address-engine.js` + catálogo POI separado; `find`/`findAcross` solo adaptadores del MISMO pipeline | Arenal Grande 2274, número incorrecto, departamentos, calles 0,0, POIs, direcUnica, error/offline. La prueba DEBE cubrir el picker de `app.js`. |
| Estados de reservas | Backend/RPC es autoridad; una sola proyección de estado de pasajeros/conductor, migrar etiquetas heredadas | Solicitud `PENDIENTE`, `ACEPTADA`, `EN_CAMINO`, `EN_PUNTO`, `EN_VIAJE`, `FINALIZADA`, `RECHAZADA`; precio fijo sin «Presupuesto aceptado». |
| Distancia, tarifa y ETA | Ruta vial verificada; precio de fuente configurable; ETA conductor→origen PRIVADA y distinta de recorrido del cliente | No mostrar ruta, vehículo ni minutos falsos. Caducidad de GPS y falta de proveedor. |
| Seguimiento | Un mismo componente GPS autorizado en tarjeta y vista ampliada; contratos Supabase por reserva | Conductor→recogida, conductor→destino, visibilidad, caducidad, pérdida de red y privacidad |
| Reserva/recepción | RPC autenticadas, idempotencia/estados y una bandeja de solicitudes en Mapa | Una solicitud produce una sola entrada; llegada a Mapa, reconexión y no duplicar notificaciones |
| Identidad | Un único conductor principal vinculado a Mapa; PIN solo vínculo/recuperación | Reinstalación segura, sesión persistente, revocación, sin PIN en texto claro ni acceso por instalar APK |
| Web/PWA/caché | La misma interfaz y contrato; HTML/JS/CSS/SW compatibles entre sí | Versión visible = commit desplegado; evitar PWA antigua v78 cuando fuente es v81+ |

**Importante:** estas son decisiones de arquitectura y objetivos de cobertura; NO afirman que todo esté implementado hoy.

## 4. Registro de defectos y prevención

Mantener `docs/REGISTRO_DE_REGRESIONES_TRASLADOS.md`, una entrada por defecto con:
- identificador, severidad P0/P1/P2, reproducción y capturas/enlace;
- versión donde FUNCIONABA, primer commit que lo rompió, explicación raíz comprobada vs hipótesis;
- prueba existente que no detectó el fallo y nueva prueba **del camino real**;
- PR/commit de solución, CI/SHA, deploy y prueba física; estado abierto/en QA/cerrado;
- contrato afectado y regla para evitar repetirlo.

Si falta dato, registrar **DESCONOCIDO**, no inventar evidencia.

## 5. Estado de versiones y evidencias (una matriz, no etiquetas confusas)

Para cada superficie informar:
- **Fuente:** rama/commit, versión en archivos;
- **Build:** workflow run, SHA, resultado;
- **Release/review:** tag, firmado/no firmado, package/versionCode;
- **Publicación:** Pages/URL/Worker/RPC efectivamente vigente;
- **Dispositivo:** instaló/no instaló, navegador/caché, probado/no probado en Samsung;
- **Bloqueantes:** problema y siguiente acción.

No llamar «última versión» al mayor número si no está publicada y comprobada. Las notas históricas con «ACTUAL» no prevalecen sobre el estado verificable en vivo.

## 6. Traspaso obligatorio entre sesiones

Al cerrar trabajo (incluso si queda incompleto), dejar **una actualización pequeña** en `docs/REANUDAR_TRASLADOS.md` o en el PR activo:
1. Último SHA, rama, PR y archivos cambiados.
2. Qué quedó implementado vs qué sigue pendiente.
3. Pruebas/CI con sus URLs y resultado; qué QA física falta.
4. Entregables y enlaces verificados; versión anterior estable/rollback.
5. Bloqueantes y **una acción exacta siguiente**.
6. Riesgos de compatibilidad y datos (específicamente qué NO se modificó).

No copiar horas de bitácora histórica como si fueran actuales. No dar por terminada una tarea si quedó un workflow ejecutando; dejar identificador del run y estado real.

**Frase de continuidad:** «Retomar Traslados Web desde Git». La nueva sesión debe consultar esta metodología **antes** de tocar código.

## 7. Definición de «Terminado»

Una función solo está **TERMINADA** cuando cumple el contrato, no rompe regresiones, CI corresponde al mismo SHA, está publicada en superficie correcta, y se verificó comportamiento de usuario en dispositivo/navegador objetivo. Si falta Samsung, publicar únicamente «compilada / disponible para revisión», no «arreglada definitivamente».

## 8. Norma de comportamiento del asistente

- Actuar sobre el repositorio existente; hacer mejoras concretas solo con alcance explícito y comprobable.
- Informar transparentemente límites, bloqueos y PR en review.
- No inventar APK, rutas, compilaciones, horas, capturas, GPS ni disponibilidad.
- Evitar cambios colaterales a Supabase, Oak & Ember o datos reales.
- Si hay urgencia de producción, preferir hotfix mínimo con regresiones antes que grandes reestructuras.
- **La continuidad depende del repositorio, no de confiar únicamente en la memoria del chat**. La memoria ayuda, pero Git es la referencia persistente y auditable.

> Aprobación de criterio: solicitud del responsable del proyecto, 18/10/2026. Este documento es un contrato de trabajo, NO una declaración de que los defectos vigentes estén resueltos.
