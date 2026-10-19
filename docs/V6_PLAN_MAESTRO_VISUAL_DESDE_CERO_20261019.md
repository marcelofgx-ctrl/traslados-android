# Plan Maestro — Traslados Visual V6 · Arquitectura de diseño limpio

**19/10/2026 · Tipo:** diseño desde cero del pasajero, sin reescribir motores.
**Estado inicial:** fase A, prototipo estático de las 4 etapas en `web-pasajero/pruebas/visual-v6/`. El prototipo NO realiza reservas, geolocalización, consultas ni navegación real.
**Motivo:** las iteraciones V1–V5.3 acumularon estilos incompatibles: encabezado recargado, tarjetas anidadas, textura persistente, demasiado scroll, CTA inaccesible y ajustes sucesivos de una superficie a costa de otra. El diseño requiere una composición única, no otro archivo de CSS correctivo.

## Producto objetivo
- Una sola PWA pasajero real en el futuro; la APK Mapa es independiente.
- Mantener identidad premium, funcional, uruguaya y sobria. Se propone contraste nuevo: **porcelana cálida + petróleo profundo**, con verde jade para estado y dorado sólo en precio/acciones. Incluye variante nocturna, ambas con la misma jerarquía.
- Regla visual: un gesto principal por pantalla, información relevante visible sin competir con otras cinco tarjetas, controles editables antes del mapa, etiquetas entendibles («Viaje» ≠ «Recogida»).
- Cabecera completamente nueva: marca pequeña, título de actividad, barra fina de progreso, etapa 01–04 discreta, botón Volver consistente. Eliminar círculos decorativos.
- Pie de flujo visible, sin tapar controles ni mapa, compatible con barra de navegación de Android y menu global.
- Mapas claros y trazas legibles. Cartografía dinámica en la implementación funcional futura; mapa del prototipo es **ilustración** y se rotula como tal.

## Cuatro pantallas coherentes
**01 · Recorrido.** Título «¿A dónde vamos?»; origen/destino en **una sola lista compacta**; mapa 190–230px dentro de viewport pequeño; tarifa/kilómetros/minutos en línea editorial (sin banda de 100px); parada opcional plegable; fila compacta conductor→recogida. CTA «Continuar».

**02 · Cuándo.** Título «¿Cuándo salimos?»; disponibilidad del conductor en una sola línea con ETA diferenciada; **ahora/10/15/otra hora** como cuatro opciones directas, y detalle horario sólo si necesario. CTA «Continuar».

**03 · Contacto.** Título «¿Con quién coordinamos?»; si hay sesión iniciada, nombre y teléfono en un bloque compacto, editable desde cuenta. Preferencias/indicaciones plegables. CTA «Revisar viaje».

**04 · Revisar.** Título «Revisá tu viaje»; importe dominante, tarifa siempre derivada del motor real en futuro; recorrido representado como timeline, recogida, pasajeros y condiciones sin cajas anidadas. CTA único «Solicitar viaje» **sólo en producción después del QA funcional y aprobación**. En el prototipo está deshabilitado.

## Método y puertas de calidad (no compilar 200 veces)
G0 — **Congelamiento**: V5.3 como referencia funcional. Nunca cambiar funcionalidad desde la maqueta. No copiar ni editar CSS V1–V5.

G1 — **Diseño autónomo**: prototipo HTML/CSS/JS 100% independiente, con dos temas y cuatro pantallas navegables. Sin Supabase, cookies, login, localStorage, Leaflet, OSM, endpoints, GPS, permisos ni envío. «Datos de ejemplo» siempre visible. No instalable como PWA productiva. No hacer builds Android. Control de alcance GitHub.

G2 — **Aprobación visual**: usuario revisa pantalla 01 en Samsung; después 02, 03 y 04. La aprobación es explícita y separada de GitHub Actions. Se solicita crítica antes de migrar.

G3 — **Kit de diseño**: tokens para color, tipografía, espaciado 4/8/12/16/24/32, botones, campos, ruta y estados; un solo módulo de UI, sin selector `body.map-first-v3` y sin capas CSS acumuladas.

G4 — **Adaptador funcional**: conectar nueva vista a contratos existentes de PWA: direcciones+paradas, ruta/caché válidos, tarifa 40 UYU/km parametrizable, ETA recogida, disponibilidad, PIN/usuario según configuración, horarios, historial y confirmación. **No reimplementar motores** ni duplicar tablas Supabase. Reusar y observar valores originales por adaptadores claros y ensayar cada contrato de forma aislada.

G5 — **QA**: pruebas por motor (0/1/2 paradas, búsqueda alternativa, cambio de dirección, ETA, historial, sesiones, Back Android, reentrada/BFCache, expiración, conexión débil, teclado, scroll, globito Samsung), pruebas visuales con anchuras 360/393/412px, dispositivo físico Samsung y comparación precio/distancia sin fingir tráfico real.

G6 — **Aprobación y promoción**: publicación inicial únicamente en `web-pasajero/pruebas/`; migración a ruta estable tras G1–G5 y aprobación visual explícita del dueño. Monitoreo y rollback. No alterar APK Mapa, Supabase, producción ni cuentas.

## Entrega y cadencia
1. **Ahora:** documento + prototipo navegable independiente con 4 pasos y 2 temas. Una sola publicación de laboratorio y capturas de referencia.
2. **Después de aprobación de estética:** completar kit de componentes y animaciones discretas. Se revisa sin API.
3. **Solo entonces:** una integración funcional progresiva por adaptadores; pruebas automatizadas antes de un despliegue piloto.
4. **Al final:** activar en PWA oficial mediante migración controlada, preservando todas las funciones.

## Prueba de aceptación de la primera iteración
- Diferencia evidente en 3 segundos: cabecera sin iconos decorativos, fondos limpios, contenido escaneable, CTA libre de overlay.
- Origen/destino y precio disponibles sin scroll desproporcionado, mapa a escala de un panel.
- Una y sólo una acción primaria por etapa; opción Volver accesible.
- Contrastes AA aproximados y tamaños táctiles >=44px; respeto por reduced-motion.
- Cambio de tema sin cambiar la jerarquía; cuatro pantallas visibles con navegación entre ellas.
- Cero llamadas de red y cero datos personales en la maqueta; ejemplos explícitos.
- No afirmar «funciona» referido al motor real por este prototipo. El usuario aprueba **la apariencia**, no el flujo operativo en este estadio.

## Fuentes de continuidad
V5.3 queda referenciada como punto de partida funcional: PR #156 y `docs/MAP_FIRST_V5_3_BALANCED_ROUTE_20261019.md`. La nueva línea V6 no hereda código visual V1–V5 y no cancela la recuperación de navegación/ETA ya resuelta.