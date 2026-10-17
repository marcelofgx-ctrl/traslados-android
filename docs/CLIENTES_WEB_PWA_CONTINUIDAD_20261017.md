# Clientes Web/PWA — continuidad y QA, 17/10/2026

## Arquitectura sin duplicados
Una página de presentación pública (`/traslados-android/`), una sola Web/PWA de pasajeros (`/traslados-android/web-pasajero/`) y una sola APK Android, Mapa Trayectos con Conductor integrado. No generar una APK Cliente ni una APK Conductor separadas.

El PR #109 fue archivado sin merge como PR independiente; su trabajo funcional pasó a `main` Web/PWA v80, y v81 agregó GPS autorizado por reserva. Esta rama de revisión parte de `main` y **no** vuelve a basarse en #109 ni modifica Mapa.

## Experiencia del pasajero
1. Inicio premium: Tu viaje, Tu reserva y Mensajería/Paquetes deshabilitada (próximamente).
2. Elegir origen/destino/paradas y cuándo; estimar kilómetros, minutos y precio solo si la ruta real está verificada.
3. Exigir cuenta en nuevas solicitudes, conservar el historial entre navegador/PWA vía cuenta Supabase, evitar duplicados y limpiar el formulario solo tras comprobante exitoso.
4. Conductor integrado recibe petición con ruta, precio y ACEPTAR/RECHAZAR directos. La entrega Android pertenece a PR #116; no dar por validada en Samsung.
5. Tras aceptar, mapa en la tarjeta del viaje:
   - EN_CAMINO: ruta GPS real **auto → punto de recogida** y destino como referencia, con kilómetros y ETA verificados.
   - EN_PUNTO: conductor informó llegada; no confundir esta confirmación con una posición GPS.
   - EN_VIAJE: ruta GPS real **auto → destino**, origen como referencia.
6. Si no llega GPS reciente o falla la estimación vial, **no** mostrar un automóvil anterior como si estuviese en ruta; mostrar claramente ruta contratada y métricas del tramo, nunca ETA fabricada.
7. El mismo componente autorizado pinta la tarjeta y la pantalla ampliada. Consultas GPS cada ~12 s solo en la vista visible, con conexión y sesión/comprobante válido. El navegador/WebView no monitoriza GPS desde un segundo plano oculto.

## Correcciones de esta rama
- `reservation-live.js`: visibilidad de consultas, invalidación de mapa viejo ante caducidad de GPS/fallo del motor, fase de llegada y encabezados correctos.
- `app.js`: pantalla ampliada comparte rastreador privado y ya no muestra un mapa estático contradictorio.
- `premium-journey.css`: presentación premium del mismo mapa en ambas vistas.
- `reservation-live-v82-smoke.cjs`: prueba sintética de visibilidad/estado, caducidad, fallo vial, EN_PUNTO y paridad.
- Ambos workflows de QA/paquete incluyen el nuevo test.

## Fronteras y bloqueantes
- La Web/PWA v81 de `main` sigue pública; esta rama **no** está desplegada. Al promover, incrementar las revisiones de HTML/Service Worker de forma consistente para invalidar cachés y preservar el funcionamiento offline.
- CI de revisión y empaquetado, más captura visual 360/393/412 y prueba física Samsung, son requisitos previos.
- Prueba real del trayecto completo: solicitud → aviso Mapa → aceptar → GPS auto/recogida → pasajero recogido → GPS auto/destino → finalizado → historial. Sin viajes de prueba ni escrituras a producción durante QA automatizado.
- Pendientes de otras iteraciones: buscador Uruguay / POIs y direcciones, precios, estados de reserva, chat, notificaciones y tratamiento de falta de disponibilidad. No afirmar que este PR los resuelve.
- No tocar datos históricos de Supabase, no purgar GPS, no afectar Oak & Ember, no publicar ni fusionar sin QA.
