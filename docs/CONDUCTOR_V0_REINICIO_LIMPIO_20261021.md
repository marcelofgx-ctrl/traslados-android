# Conductor V0 — reinicio desde cero (21/10/2026)

## Decisión autorizada
El Conductor legado queda **archivado y fuera del desarrollo activo**; el PR #185 se cerró sin fusionar.

Respaldo inmutable por referencia:
- GitHub: rama `archive/conductor-pr185-20261021`
- Commit: `8705c2bce5c87175d60290797428b0bc34e57538`
- PR retirado: #185.
- Origen del reinicio: `main`, commit `c3614f62c9f9d14707b26dadacb311d10e834a1d`
- Nueva rama: `feat/conductor-v0-limpio-20261021`

**NO incorporar cambios del PR #185, cherry-picks ni copias de sus clases Java.** Se permite consultar el comportamiento previo solo como documentación de requisitos, nunca como base de implementación.

## Producto y navegación
- **Una sola APK Mapa Trayectos**. No crear APK Conductor/Cliente.
- Conductor es una pantalla nativa dentro de la actividad de Mapa, no otra Activity, WebView ni un proceso aparte.
- Deslizar de derecha a izquierda en Mapa abre Conductor. Deslizar de izquierda a derecha en Conductor regresa a Mapa. Sin controles flotantes enormes.
- Conductor **no contiene PIN, login ni pantalla «sin vincular»**. Identidad y permisos pertenecen a Mapa y se suministran mediante una interfaz segura explícita; no acceder a `driver_session.pin` ni importar clases de `uy.com.traslados.conductor`.
- Jornada Play/Stop y GPS pertenecen únicamente a Mapa. No crear un segundo motor de jornada, GPS, ETA o reservas.

## Diseño aprobado (no reinterpretar)
La fuente visual es **la imagen original aportada y aprobada por el usuario**. Reproducirla con controles Android funcionales:
- Fondo exterior negro; un solo panel grande gris carbón, redondeado.
- Cabecera QUICK TRIP / Conductor, indicador de jornada a la derecha.
- Tres tarjetas negras de contadores: Nuevas, Aceptadas, En viaje.
- Una tarjeta negra de solicitud con origen, destino, distancia, duración, importe y dos acciones: Rechazar gris y Aceptar verde.
- Mensaje discreto de deslizamiento para volver a Mapa.
- Si no hay viajes: mantener la misma composición, mostrar un estado vacío honesto. Nunca simular viajes ni información.
- Historial y viajes programados tendrán vista y flujo acordados en una etapa posterior; no mezclar opciones en la solicitud inmediata.

## Dependencias legadas identificadas — cortar con seguridad
`main` **todavía contiene** la versión antigua, porque este documento no elimina ni sustituye código de producción:
- `mapatrayectos/build.gradle` incorpora el código de `conductor/src/main/java` mediante `prepareEmbeddedConductor` (transforma código Java y recursos).
- `mapatrayectos/src/main/AndroidManifest.xml` registra la pantalla antigua y sus servicios.
- `MainActivity.java` abre la pantalla antigua y utiliza las RPC heredadas desde avisos.
- `TransferAlerts.java`, `TransferQuickPanel.java` y `TrackingService.java` poseen dependencias de reservas/avisos con el conductor viejo.
- `settings.gradle` incluye un proyecto `conductor`; no eliminarlo en `main` sin demostrar que la app estable y sus pipelines siguen funcionando.
- El PR #185 dejó *migraciones en Supabase ya aplicadas*. Cerrar el PR no las revierte. No ejecutar DROP ni modificar datos históricos sin revisión separada.

### Secuencia estricta y verificable
**Fase 0 (actual):** respaldo + PR viejo cerrado + rama nueva, criterios pactados. Ninguna APK.
**Fase 1:** implementar solamente la pantalla nativa Conductor V0 exacta a la imagen. Sin red, credenciales, cambios en GPS ni reservas. Pantalla vacía de prueba sin datos inventados. Aprobar diseño antes de avanzar.
**Fase 2:** sustituir el acceso de Mapa por la transición interna; retirar los puntos de entrada del Conductor heredado y aislar sus compilaciones, asegurando que Mapa conserve sus funciones. No introducir PIN ni pantallas ajenas.
**Fase 3:** conectar a través de un contrato nuevo, pequeño y seguro para solicitudes existentes. Cada acción debe pasar pruebas reales. No reutilizar el controlador de la pantalla anterior.
**Fase 4:** verificar Play→disponibilidad→GPS fresco→solicitud→aceptar/rechazar→ETA en teléfono físico. Solo entonces publicación oficial.

**Regla de ejecución:** cambio único y comprobable por fase. No generar APK ni fusionar modificaciones hasta aprobar la pantalla y las pruebas pertinentes. No tocar los motores estables, algoritmos de rutas, buscador, web de pasajeros ni tarifas. No borrar registros operativos.

## Estado al crear este documento
- Archivo de PR anterior: realizado.
- Rama limpia: realizada.
- Análisis de dependencias críticas: inicial, requiere barrido completo antes de eliminar clases heredadas.
- Eliminación física de código legado: **no realizada**; sería inseguro sin separar servicios y referencias que utiliza Mapa.
- Nueva pantalla nativa: **componente V0 creado**, `mapatrayectos/src/main/java/uy/com/mapatrayectos/conductorv0/ConductorV0View.java`, sin importaciones legadas, sin PIN, sin conexión ni lógica GPS. Todavía no está conectado a la aplicación ni fue compilado; no reemplaza la pantalla vieja.
- Fase 1 pendiente: revisar la pantalla integrada y aprobar su renderizado real antes de avanzar a la transición.
- No hay compilación APK ni PR nuevos; la rama está aislada de `main`.
- Backend de pasajero/Supabase y app instalada: sin cambios en esta rama.
