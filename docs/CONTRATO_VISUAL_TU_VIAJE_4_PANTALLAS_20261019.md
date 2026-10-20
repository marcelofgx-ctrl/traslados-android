# CONTRATO VISUAL BLOQUEADO — TU VIAJE 1/4 A 4/4
**Aprobación del usuario:** 19/10/2026. **Estado:** referencia visual única, vinculante para implementación.  
**Repositorio:** `marcelofgx-ctrl/traslados-android`.  
**Superficie:** Web/PWA pasajero. **No** crear APK Cliente; Android nativa sigue siendo **Mapa Trayectos**.

## Decisión inequívoca
Las capturas que el usuario adjuntó en su última indicación son **la única referencia visual** para las cuatro pantallas «Tu viaje · 1/4, 2/4, 3/4, 4/4». Son **las capturas de la antigua serie funcional MAP-FIRST/VISUAL V5** con encabezado compacto **«Tu viaje» + contador fraccional**, mapa vial claro, tarifa y tarjetas. No se reinterpretan ni se mezclan con las maquetas Quick Trip V6 del logo grande.

Los importes, dirección, nombres, distancia al conductor, ETA y coordenadas en capturas son **ejemplos del instante capturado**, no datos a fijar. Solo la **composición, tipografía, color, tamaño, orden, iconografía y comportamiento** son vinculantes. Las cuatro pantallas usarán **datos y motores reales** existentes, sin `placeholder` engañoso.

### Alcance estético común: obligatorio
- Fondo petróleo oscuro con patrón diagonal sutil y tarjetas de tonos verde petróleo; acentos champagne/dorado suaves; textos legibles y jerarquía idéntica a capturas aprobadas.
- **Encabezado de flujo compacto**: círculo/botón de volver, icono auto, etiqueta **«Tu viaje»** y pastilla **«N / 4»**. No insertar un segundo encabezado ni una barra de progreso distinta.
- **Navegación inferior** permanente con **Inicio, Mis traslados, Menú**, una sola fila de iconos y rótulos, sin iconografía duplicada.
- Márgenes, radios de bordes, escala tipográfica, posiciones y espaciado coincidentes con las capturas **en Samsung (ancho objetivo ~393 px CSS; comprobar 360–430 px)**. El navegador, la barra Android y las superposiciones de otras apps no forman parte de la web.
- El botón de avance debe ser visible, accesible y no estar tapado por la barra inferior, sin encimar campos ni mapa; retroceso mantiene datos. Conservar el rótulo específico de cada etapa.
- No generar otra propuesta estética, cambiar paleta o “mejorar” a criterio propio. Una alteración respecto a capturas exige evidencia del problema y autorización explícita del usuario.

## Pantalla 1/4 — Recorrido
**Secuencia exacta aprobada:**
1. Encabezado «Tu viaje» + «1 / 4».
2. **Origen** y **Destino** con marcadores verde/rojo, dirección real y botón **Editar** para cada uno.
3. **Mapa vial claro**, rectángulo grande, esquinas redondeadas, puntos origen/destino y traza sobre calles. Crédito OpenStreetMap si corresponde.
4. Franja oscura «Ruta estimada del viaje por calles · sin tráfico en vivo».
5. Panel de precio con **PRECIO ORIENTATIVO DEL VIAJE** destacado, **DISTANCIA DEL VIAJE** y **DURACIÓN ESTIMADA**. Los valores se calculan con motor existente, no se fijan los $124 o 3,1 km de referencia.
6. **Agregar paradas (opcional)**, plegable con lógica de paradas existente.
7. Estado del conductor, **distancia al origen** y **llegada estimada**, con cadencia y estado reales. Mensajes discretos según disponibilidad, sin afirmar disponibilidad cuando no esté confirmada.
8. Botón **Continuar**.

**No admitido:** título «¿Dónde te buscamos?» precediendo estos bloques; duplicado «Mi punto de recogida» arriba; enormes tarjetas «Recogida por confirmar» o «Calcular llegada desde mi ubicación» bloqueando el acceso a origen/destino; mapa negro cuando existe el mapa claro real. La acción GPS sigue disponible contextualmente en Editar origen o donde sea funcionalmente necesaria, sin duplicarla.

## Pantalla 2/4 — Horario
1. Encabezado «Tu viaje» + «2 / 4».
2. Estado compacto **Conductor disponible para recogerte** y cifras auténticas si están disponibles.
3. Título **«¿Cuándo querés viajar?»** y bajada **«Ahora o en las próximas 2 horas.»**
4. **Grilla de dos columnas**: Ahora, En 10 minutos, En 15 minutos, En 20 minutos, En 30 minutos, En 45 minutos, En 1 hora, Elegir otra hora. Selección dorada visible.
5. Barra desplegable **«Disponibilidad y pasajeros · ver detalles»**, sin tarjeta flotante suelta fuera de la grilla.
6. Botones **← Atrás** y **Continuar**, en sus ubicaciones y tamaños de la captura.

**No admitido:** un segundo encabezado con cuatro círculos, márgenes que expulsen la última columna de horarios ni controles que activen horas que el backend no acepte.

## Pantalla 3/4 — Tus datos
1. Encabezado «Tu viaje» + «3 / 4».
2. Tarjeta compacta con disponibilidad, distancia y ETA si existe información auténtica.
3. Título **«Tus datos»**, subtítulo **«Tus datos permiten coordinar el traslado.»**
4. Tarjeta **«DATOS DE MI CUENTA»**, **Nombre y apellido**, **Teléfono de contacto**, enlace **«Ver mi cuenta»**, usando **la sesión y los datos originales**; permitir edición y actualización por el flujo original.
5. Nota **«Estos datos son solo para coordinar tu traslado.»**
6. Acordeón **«Indicaciones, email y preferencias (opcional)»** con controles nativos y guardado original.
7. **← Atrás** y **Revisar mi viaje**.

**No admitido:** «Pasajero de ejemplo», teléfono ficticio, campos inventados ni datos de cuenta no verificados.

## Pantalla 4/4 — Confirmar
1. Encabezado «Tu viaje» + «4 / 4».
2. Tarjeta superior **«TRASLADOS · TU PRÓXIMO VIAJE»**, estado **«SIN ENVIAR»** hasta envío efectivo, encabezado **«Todo listo para solicitar»**.
3. Estado recogida **«Recogida ahora»** o el horario REAL que eligió el pasajero.
4. Panel principal de **Precio del viaje** grande y dorado, **Distancia**, **Duración**.
5. Tarjeta **«TU RECORRIDO»**: origen y destino reales, con **«Editar recorrido»** que vuelve a 1/4 conservando los campos.
6. Etiquetas de horario y pasajeros (cantidad real, no fijar «1» si varía).
7. **«Comprobá los datos y continuá»** y botón original para enviar la solicitud verdadera. Estados pendiente/confirmado/error honestos.

**No admitido:** esconder el botón final debajo de la barra fija; enviar reservas de prueba automáticamente; duplicar la llamada RPC de reserva; tratar «SIN ENVIAR» como enviado.

## Contrato funcional inmutable
- Solo una PWA cliente real. Reutilizar `web-pasajero/app.js`, `premium-journey.js`, `premium-map.js`, `route-engine.js`, `eta-engine.js`, `booking-steps.js`, `booking-contracts.js`, `reservation-live.js` y Supabase existente.
- **No romper** geocodificación, alternativa de búsqueda, edición origen/destino, paradas, cálculo por calles, tarifa parametrizada, ETA, GPS, agenda, identidad, historial, seguimiento ni envío.
- Mantener advertencias auténticas cuando falla una consulta; no maquillarlas como disponibilidad.
- El CSS de presentación no debe ocultar el componente real necesario para un flujo ni forzar “éxito” de una operación fallida.
- Cualquier reforma de `main` debe ir en rama/PR aislado; pruebas de regresión y capturas Samsung antes de declarar validación visual.

## Checklist de aceptación (todas las casillas requeridas)
### Identidad y coherencia
- [ ] Las **cuatro** pantallas usan «Tu viaje» y contador «1 / 4» … «4 / 4»; sin logo Quick Trip grande ni segundo rail de pasos.
- [ ] No hay mensajes «MAP-FIRST V4.2», «VISUAL V5», «V6 experimental», «SIN RESERVAS» en la versión operativa.
- [ ] Barra inferior con solo **tres** iconos/rótulos (sin duplicados); menú, historial e inicio funcionan.
- [ ] Comparación visual capturas aprobadas vs Samsung: orden, tamaños, radios, color, tipografía, espaciados y continuidad de CTA iguales en cada pantalla, a la misma resolución.

### Recorrido
- [ ] Al ingresar, origen y destino aparecen **antes del mapa**, con botones Editar reales.
- [ ] Mapa vial claro con línea y marcadores reales, sin rutas ilustrativas.
- [ ] Precio/km/min y origen-destino cambian coherentemente al editar; paradas funcionan.
- [ ] ETA y distancia conductor no muestran cifras fijas y se refrescan según lógica de producción.
- [ ] CTA Continuar visible, valida ruta y permite avanzar sin perder datos.

### Cuándo
- [ ] Ocho opciones en grilla de dos columnas alineadas y dentro de pantalla.
- [ ] «Elegir otra hora» abre selector real y valida límites del sistema.
- [ ] Acordeón de disponibilidad/pasajeros no desordena la grilla.
- [ ] Atrás y Continuar hacen lo correspondiente.

### Tus datos
- [ ] Datos de cuenta auténticos, teléfono verdadero y edición mediante cuenta original.
- [ ] Indicaciones/email/preferencias plegables y funcionales.
- [ ] Revisar mi viaje valida datos y llega a confirmación.

### Confirmación
- [ ] Precio, distancia, duración, recorrido, horario y pasajeros coinciden con los pasos anteriores.
- [ ] «SIN ENVIAR» se mantiene hasta envío auténtico; no confirmación falsa.
- [ ] Editar recorrido conserva datos y regresa a 1/4.
- [ ] Confirmar usa RPC original exactamente una vez, errores visibles, sin duplicar reservas.

### Publicación
- [ ] Pruebas automatizadas PWA y alcance sin errores.
- [ ] Capturas tomadas y contrastadas en Samsung, modo oscuro, con y sin barra de navegador visible.
- [ ] Prueba de navegación/scroll/teclado/tamaño de pantalla sin superposiciones del footer.
- [ ] GitHub Pages confirma el SHA publicado; enlace exacto funciona.
- [ ] El usuario valida fidelidad visual antes de declarar **«idéntico»**. CI estática no equivale a prueba visual.

## No confundir versiones
- **Referencia visual para «Tu viaje»:** únicamente las nueve capturas del último mensaje del usuario, en particular el encabezado compacto 1/4–4/4 y sus tarjetas y botones.
- Las maquetas Quick Trip V6 anteriores permanecen como historia de diseño; **NO** son contrato de estos cuatro pasos.
- **Portada con tres servicios:** decisión de navegación independiente. Mantener existente sin introducir un segundo diseño dentro de «Tu viaje».
- El objetivo no es completar otra maqueta: es **paridad visual 1:1 más funcionalidad real**.

## Criterio de entrega
**Rechazar una entrega** que funcione pero cambie la composición, que se vea igual pero use datos falsos, o que muestre el botón Continuar sin permitir completar el flujo. El resultado debe satisfacer las tres condiciones simultáneamente.
