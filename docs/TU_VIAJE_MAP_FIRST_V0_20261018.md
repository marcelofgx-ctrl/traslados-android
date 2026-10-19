# TU-VIAJE-MAP-FIRST-V0 — brief técnico de prueba (18/10/2026)

**Objetivo:** evaluar con Marcelo una interfaz pasajero compacta e inspirada en referencia de tres móviles: mapa protagonista, estado inequívoco y precio/km/min en una única banda. Preservar identidad Traslados petróleo/champagne y metodología G0–G6.

## G0: Contexto existente y dependencias
- Arquitectura oficial: página pública, Web/PWA pasajero, APK Android única Mapa Trayectos. NO crear APK Cliente ni APK Conductor.
- PWA v82 en main, PR #145 (UX-RECORRIDO-001) abierto y sin publicar a fecha de creación.
- No rehacer ni pisar `web-pasajero/premium-route.js`, `premium-map.js`, `app.js` ni la cadena de GPS/ETA. El PR #145 implementa resumen precio/km/min y mapa vial verificado en la UI actual.
- Conservación de trazas e histórico sin purga. GPS comercial y recogida son otro subsistema independiente.

## G1: Alcance de esta v0
- **Artefacto único nuevo:** `web-pasajero/experiments/tu-viaje-map-first-v0/index.html`.
- **Documentación:** este archivo.
- Solo diseño interactivo OFFLINE, sin bibliotecas ni fuentes remotas. Tres estados simulados: solicitud enviada, conductor en camino y viaje en curso.
- Componentes: origen/destino compacto editable (sin geocodificación), mapa esquemático explícitamente ilustrativo, banda precio/km/duración, estado principal, conductor demo, acciones de detalle/chat (sin envío real).
- No usar credenciales, mapas reales, GPS privado/comercial, Supabase, API tarifas, cuentas, reserva real, service worker ni almacenamiento persistente. No publicar por GitHub Pages.
- Todos los importes, tiempos y trazas de la pantalla son **ficticios o exclusivamente ejemplificativos**. No se debe interpretar como funcionamiento de GPS y rutas reales.

## G2: Contratos de diseño a validar con el usuario
1. Estado de viaje identificable sin scroll y sin lenguaje contradictorio.
2. Mapa visual domina pero no desplaza precio/datos imprescindibles.
3. Recogida conductor→origen y traslado origen→destino son rutas distintas.
4. Resumen único con precio destacado, km viaje y minutos viaje; ETA conductor separada.
5. Acciones accesibles mediante controles claros, detalles secundarios plegables.
6. Boceto adaptado a viewport Android de 360–412 px, texto legible y contraste.
7. El globito Mapa Android no es parte de este experimento: no duplicarlo en Web/PWA.
8. Retener la estética petróleo/champagne con traza verde moderada.

## G3: Escenarios de exploración en móvil
- Cambiar entre Pedido, Recogida, En viaje; comprobar encabezado, ubicación de automóvil de muestra, etiquetas y mapa.
- Abrir/Cerrar detalles y chat; escribir mensaje de ejemplo; no se envía a backend.
- Editar origen/destino y guardar: solo cambia texto, **no recalcula ruta ni precio**; se advierte.
- Probar en pantalla pequeña y con fuente Android ampliada; evitar superposición de botones.
- Confirmar si se entiende la diferencia de ETA de recogida y km/min de viaje.

## G4: QA mínimo al evolucionar
- Archivo HTML autocontenido, sin peticiones de red externas (CSP opcional más adelante).
- Comprobar navegabilidad por teclado, aria-pressed/aria-live y estados operativos.
- Probar 360/390/412 px y desplazamiento con footer fijo.
- Contrastar con PR #145 y documentar elementos a fusionar, sin duplicar motores de rutas, precios, búsquedas o chat.
- QA funcional real sobre PWA solo en una rama futura tras decisión de diseño. Una imagen o una prueba estática no valida GPS real.

## G5: Política de integración
**No fusionar este prototipo tal cual a main**, ya que contiene datos simulados. Si el usuario aprueba la experiencia, abrir un PR distinto de implementación productiva partiendo del último head compatible de PWA y del PR #145; extraer solo estructura visual y estilos, conectar fuentes de verdad ya existentes. Exigir CI y QA Samsung antes de despliegue.

## G6: Estado
- Experimento aislado: creado y versionado en GitHub.
- Publicación estable: NO.
- Aplicación Android: SIN CAMBIOS.
- Backend/Supabase: SIN CAMBIOS.
- Estado de validación por usuario: PENDIENTE.
