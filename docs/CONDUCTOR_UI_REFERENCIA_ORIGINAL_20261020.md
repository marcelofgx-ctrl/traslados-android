# Conductor · referencia visual original aprobada

Referencia: imagen original adjuntada por el usuario en el hilo (archivo `1000365024.jpg`, 1128×1536), **no** las reinterpretaciones con dorado. Esta imagen es la fuente de verdad para el diseño de Conductor en Mapa.

## Contrato visual

1. Lienzo negro completo, margen oscuro limpio sin controles flotantes.
2. Un único gran panel gris carbón `#252525`, redondeado ~24dp, sin textura, sin degradados ni brillos.
3. Cabecera a la izquierda: `QUICK TRIP` gris claro y debajo `Conductor` blanco grande en tipografía simple negrita. A la derecha, indicador verde y texto `Jornada activa` si corresponde; estado real, sin simular disponibilidad.
4. Bajo la cabecera: exactamente **tres** recuadros negros, alineados: `Nuevas`, `Aceptadas`, `En viaje`. Números blancos prominentes, etiqueta gris, sin bordes metálicos.
5. Luego, **una** tarjeta negra grande, redondeada. Cuando hay una solicitud real: título `Nueva solicitud`, origen verde, destino rojo, direcciones blancas, separador, `DISTANCIA`, `DURACIÓN`, `IMPORTE`. Debajo, botones `Rechazar` gris `#2F2F2F` y `Aceptar` verde `#007859`.
6. Sin solicitudes o sin vinculación: no inventar viajes/valores. Conservar el mismo lenguaje visual y mostrar estado honesto.
7. Pie discreto: `➜ Deslizar a la derecha para volver a Mapa`.
8. Sin superposición de mapa/controles, sin PIN en la vista Conductor, sin pantallas Android independientes.
9. Historial y etapas de viaje siguen siendo funcionales, sin saturar la tarjeta de solicitud pendiente.
10. Fuente de datos: backend de reservas autorizado y estado real de jornada/GPS. No codificar permanentemente `18 de Julio`, `Aeropuerto` ni `$840`: aparecen únicamente en la imagen de referencia.

## Implementación

`mapatrayectos/src/main/java/uy/com/mapatrayectos/driver/UnifiedDriverPanel.java` (nativo en `MainActivity` Mapa); conserva `UnifiedDriverGateway`, `UnifiedDriverPolicy`, monitor y fuente de datos. No modificar mapa, rutas, precios, backend o firma únicamente por cambios visuales.

## QA de aceptación

- Android Java + Gradle en verde.
- Captura Android con negro de fondo, gris carbón, 3 contadores, estado vacío sin datos ficticios.
- Con reserva de prueba autorizada: tarjeta y acciones del patrón de la imagen, con valores devueltos por Supabase.
- Deslizamiento mapa→conductor→mapa sin reabrir Activity ni perder jornada.
- Validación física Samsung antes de fusionar con `main`.
- La APK QA actual utiliza un identificador por compilación y **no actualiza** QA anteriores. No presentarla como actualización hasta resolver paquete y firma estables.
