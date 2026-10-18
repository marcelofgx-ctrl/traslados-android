# CONTINUIDAD DE SESIÓN — metodología, regresiones, UX y evidencias (18/10/2026)

> **Nueva sesión, primer paso:** leer [METODOLOGÍA PERMANENTE](METODOLOGIA_PERMANENTE_TRASLADOS.md), [REGISTRO DE REGRESIONES](REGISTRO_DE_REGRESIONES_TRASLADOS.md), [REANUDAR](REANUDAR_TRASLADOS.md), estado automático y PR enlazados. **No rehacer funcionalidades ni confundir PR, compilación y publicación.**
>
> **Material privado:** el usuario dispone de un ZIP descargable `CONTEXTO_MAESTRO_TRASLADOS_2026-10-18.zip` con este contexto ampliado, **12 capturas reales**, un índice de cada captura y lámina de contacto. **No subir estas capturas a este repositorio público**: contienen direcciones y referencias de reservas. Pedir que adjunte el ZIP si hacen falta las imágenes originales en otra sesión.

## 1. Producto DEFINITIVO (sin volver a discutirlo)

1. **Página web pública**: presentación y enlace de reserva, `https://marcelofgx-ctrl.github.io/traslados-android/`.
2. **Web/PWA pasajeros única**: `https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/`; misma lógica navegador/PWA instalada. **NO producir APK Cliente.**
3. **UNA APK Android Mapa Trayectos** con **Conductor integrado**: GPS, jornada, mapa, burbuja, solicitudes, aceptación/rechazo, estados, alertas, mensajes, trayectos, histórico. **NO producir APK Conductor independiente.**

Backend Supabase *Traslados* `zetaudvvutlouiqxopvg`, **jamás Oak & Ember**. No borrar viajes, trazas GPS, reservas, cuentas, credenciales ni configuración. Configuración tarifa actual UYU 40/km; para viajes inmediatos con precio cerrado el conductor **solo acepta/rechaza**, sin presupuesto posterior. No romper el flujo legado de presupuestos programados que todavía esté en uso. El conductor principal debería vincular su teléfono UNA vez, no ingresar PIN diariamente; no considerar instalar APK como autorización.

## 2. Lección de esta sesión: por qué reaparecían errores

La investigación de Git reveló que reparaciones previas se perdieron al crear nuevas implementaciones que no reutilizaban su lógica. Los tests a veces comprobaban el **helper antiguo**, no el formulario del pasajero que ahora ejecuta **otro camino**. Hubo deriva entre ramas PR, releases de revisión, PWA principal/caché y pruebas Samsung. Las métricas CI verdes no equivalen a prueba de usuario. Adoptar G0→G6 en la metodología: **reproducir**, identificar raíz/historia, contrato único, test ROJO real, fix mínimo, test VERDE+regresiones, integración+QA, publicación comprobada con rollback, documentación final. No promover con puertas pendientes.

## 3. Estado de ramas y entregas — corte 18/10/2026

Verificado al cierre; **volver a consultar GitHub en vivo**.

- **main:** `c0783fe25d853deb39257c02499314faa3fa3e90`. PWA main **v81**; SW principal `traslados-pwa-v81-unificada-20261017-gps-fases`.
- Cadena Android **todos PR abiertos/draft, ninguno fusionado**: [#110](https://github.com/marcelofgx-ctrl/traslados-android/pull/110) conservar datos → [#113](https://github.com/marcelofgx-ctrl/traslados-android/pull/113) GPS privado por reserva → [#116](https://github.com/marcelofgx-ctrl/traslados-android/pull/116) aceptación directa integrado R25.28 → [#120](https://github.com/marcelofgx-ctrl/traslados-android/pull/120) monitor/reconexión R25.29 → [#121](https://github.com/marcelofgx-ctrl/traslados-android/pull/121) identidad única cifrada R25.30.
- Android R25.30 **release firmado de revisión** ([tag](https://github.com/marcelofgx-ctrl/traslados-android/releases/tag/mapa-r25.30-identidad-review-20261018)), versionCode82 y mismo package `uy.com.mapatrayectos`; **QA real Samsung no certificada**. R25.29 también tiene prerelease firmado. No llamar estable a un prerelease.
- Cadena PWA: [#119](https://github.com/marcelofgx-ctrl/traslados-android/pull/119) GPS y fases unificados, base main, abierto/draft → [#122](https://github.com/marcelofgx-ctrl/traslados-android/pull/122) fix buscador DIR-001, base #119, abierto/draft.
- **#122 SHA final:** `19bd4e625891c17f681da3db63f4c81bb1132eda`. [CI review SUCCESS 39118332088](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/39118332088), [CI package SUCCESS 39118332074](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/39118332074). **NO FUSIONADO / NO DESPLEGADO**. Historial prueba real del picker [ROJA antes de fix 39117993289](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/39117993289). No afirmar que Arenal Grande tenga coordenadas reales correctas solo por IDE sintético.
- Dejar claras las dependencias #119→#122 y #110→#113→#116→#120→#121 antes de aprobar fusiones.

## 4. Prioridades y propuestas confirmadas por las 12 capturas del usuario

### DIR-001 · P0 · **buscador, regresión histórica**

El formulario de origen/destino no encontraba una dirección con altura elegida en Montevideo, pese a que el fallback `direcUnica` se había añadido en PWA v29. En v75 (commit `298f4ffe8374`) se introdujo `findAcross()` para otros departamentos y `app.js` pasó a usarla, pero ella no llamaba el fallback que ya tenía `find()`. La prueba antigua solo ejercitaba `find()`, por eso se aprobaba a pesar del error visible.

**#122** agrega núcleo canónico `searchVerified()` compartido, fallback número exacto por departamento, validación de coordenadas/calle/altura sin aceptar números aproximados como exactos, nueva prueba que ejecuta **picker real** extraído de `app.js`, caché de direcciones `?v=3` + SW revisado; pruebas de revisión/empaquetado en verde. **Falta** consulta real IDE, Samsung, integración con #119 y publicación comprobada. Criterios adicionales: POIs (aeropuerto y centros comerciales), paradas, otros departamentos, respuesta lenta, servicio caído, dirección que verdaderamente no existe, 0,0. No hacer otro motor paralelo.

### STATUS-001 · P0 · **historial de precio fijo**

Capturas de viaje confirmado con UYU124 para ~3,1 km y texto **«Presupuesto aceptado»** en las etapas, heredado del flujo antiguo; en otros bloques insiste en explicar «El conductor acepta o rechaza» incluso luego de confirmar. `app.js::tripTimeline`, `trip-board.js` y Conductor Android interpretan estados por distintos caminos. **Siguiente tarea P0:** contrato/representación única por modalidad, mantener datos/timestamps y presupuestos LEGADOS, prueba ROJA de UI en `fare_mode=fixed` antes del fix, y VERDE posterior. No asumir horarios que no existan.

### GPS-001 · P0 · **seguimiento realmente vivo**

Capturas de `Mis traslados` muestran trazado entre origen **O** y destino **D** con km/duración **del recorrido contratado**. Aviso «Sin posición GPS reciente del auto» es correcto: **ese mapa no representa dónde está el conductor**. Se requieren dos fases privadas y verídicas: al aceptar **conductor→recogida**, al empezar **conductor→destino**, con ETA real, caducidad/permiso, y representación honesta en ausencia de GPS. Tarjeta y mapa ampliado deben usar el mismo componente; #119 lo aborda, QA pendiente. Mantener consentimiento y privacidad. Preferencia de diseño: mapa claro, traza verde sobria y marcadores vivos, sin inventar GPS.

### MON-001 · P0 · recepción y avisos

Una captura muestra el globo nativo de **nuevo traslado, aceptar/rechazar con precio cerrado** encima del navegador del pasajero, y otra muestra notificación web de confirmación. Ese caso demuestra solicitud→conductor→confirmación, pero **no demuestra disponibilidad persistente con Mapa cerrada**. R25.29 review #120 agregó verificación de servicio, monitor heartbeat, polling directo visible, botón diagnóstico, deduplicación. Verificar en Samsung con Mapa abierta/minimizada, permisos, batería, PIN, una sola alerta por petición, no generar viajes de prueba en producción sin permiso. Sin FCM no asegurar 24/7.

### AUTH-001 · P1 · identidad conductor

Usuario rechazó PIN repetido: Mapa debe reconocer al principal **después de vinculación validada una sola vez**. #121 R25.30 review propone Keystore, migración conservadora, nuevo teléfono exige vinculación, GPS mantiene consentimiento separado, extensible a varios conductores en futuro. QA Samsung aún necesaria.

### CACHE-001 · P1 · PWA antigua

Captura banner **«PRUEBA OPERATIVA v78»**, mientras main es PWA v81. Diagnosticar realmente caché/SW, Página servida, versión assets y PWA instalada. No atribuir sin prueba ni borrar datos del pasajero. #122 cambia caché de direcciones en review, no ha sido publicada.

### UI / propuesta de diseño (posterior a P0)

- **Instalar PWA:** capturas de pantalla de instalación larguísima, «Ver cómo instalar» y pasos de Samsung duplicados, excesivo scroll. Reducir a CTA primaria + instrucciones por navegador plegables. No generar otra APK.
- **Ficha del viaje:** demasiadas tarjetas largas, repite precio y condiciones; hacer visible «Conductor en camino», ETA/km reales, lugar de recogida, destino y precio; otros datos plegados por defecto, chat fácil.
- **Estados:** eliminar «presupuesto aceptado» para tarifa fija; jerarquía temporal clara.
- **UI mapa:** distinguir ruta reservada/auto real, evitar dibujo engañoso, mapa legible y texturas premium petróleo/champagne/dorado; trazado verde elegante, no flúo.
- **Globo de Mapa:** en las capturas flota encima de Chrome porque el MISMO Samsung está ejecutando Mapa Conductor. No culpar a un componente de PWA; mejorar posición, tamaño, visibilidad y no bloquear controles.
- **Búsqueda:** mensaje «no encontrada» solo cuando corresponde; alternativas útiles, botón visible y sugerencias precisas.
- Conservar portada separada «Tu viaje»/reservas, formularios plegables, ahora/10 minutos/otro, permisos explícitos y formato 24 h.

## 5. Qué mostraban las imágenes privadas (12 adjuntos JPG; índices en ZIP del usuario)

1. Formulario antiguo con banner de prueba v78, disponibilidad negativa y sin ETA.
2. Resumen antes de confirmar con recorrido km/tiempo y aviso de recogida pendiente.
3. Pantalla solicitud enviada con precio fijo y código.
4. **Burbuja nativa** de nueva petición con botones aceptar/rechazar encima de Chrome.
5. Notificación web de cambio de estado a confirmado.
6. Mis traslados tarjeta confirmada/mapa O→D, 3,1 km/7 min.
7. Trayecto contratado señalado como tal, sin GPS reciente del conductor.
8. Tarjeta con desplegables y chat.
9. Timeline heredado «Presupuesto aceptado».
10–11. Página PWA instalación extensa, instrucciones duplicadas Samsung Internet.
12. Picker de dirección con «No encontramos…» y cartografía oscura.

Los originales de estas doce imágenes **NO están versionados en GitHub**; se entregaron al usuario dentro del paquete ZIP privado. Solicitar que adjunte ese ZIP en nueva sesión si es necesario revisar píxeles/elementos concretos.

## 6. Próximo trabajo, en orden

1. Aplicar metodología, leer este documento, comprobar SHA/CI/PR/Pages y si #119/#122 siguen abiertos.
2. G5 DIR-001: consulta a IDE real + UI en Samsung/caché; plan de integrar #119→#122; sin declarar resuelto con CI solamente.
3. Iniciar STATUS-001: test inicial ROJO del timeline en precio cerrado; fuente única de estados; corregir sin alterar legados; test verde.
4. Completar GPS #119 con rutas por fases y sincronización, sin exposición privada.
5. QA recepción/identidad Mapa R25.30 de prerelease (una APK; no borrar ni desinstalar).
6. Más tarde simplificar instalación y UI, dejando intacto el flujo funcional.
7. Entregar siempre accesos **verificados** a tres superficies, compilaciones, publicación, pruebas físicas/pendientes, rollback, nueva actualización de continuidad.

**Frase para el próximo chat:** «Retomar Traslados Web desde Git. Traigo el paquete de continuidad y 12 capturas del 18/10/2026. Leé la metodología permanente y el registro de regresiones. Seguí desde #122 y #119 sin rehacer ni publicar apresuradamente; después STATUS-001, GPS, monitor/identidad Mapa y propuestas visuales.»
