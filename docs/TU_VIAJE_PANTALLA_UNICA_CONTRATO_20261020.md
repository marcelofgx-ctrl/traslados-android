# Tu viaje — pantalla única (contrato de implementación)
Fecha: 2026-10-20
Rama de trabajo aislada; no modificar motores ni desplegar a main sin pruebas.

## Diseño aprobado
- Una sola pantalla; eliminar navegación visual 1/4–4/4 SOLO en el nuevo componente.
- Cabecera con logo real Quick Trip, no ícono genérico de automóvil.
- Origen y destino siempre visibles; conservar buscador, GPS, paradas y sus handlers originales.
- Botón pequeño de historial junto a destino: panel desplegable animado, ocultable por selección, botón o toque exterior; mostrar destinos reales de reservas del pasajero autenticado, no datos de demostración. Si no hay registros, estado vacío. Selección rellena destino mediante el flujo existente y recalcula ruta/tarifa/ETA, sin crear reserva.
- Mapa real siempre visible; distancia de recogida y ETA reales, sin simulaciones.
- Viajas ahora seleccionado por defecto; horario alternativo en desplegable compacto usando la lógica existente.
- Precio, kilómetros y duración visibles al estar calculados; solicitar viaje usa el botón/handler original, una sola vez.
- Historial completo en navegación secundaria; portada de tres servicios intacta.
- Fondo petróleo, detalles champagne; sin textos de PRUEBA ni códigos internos en interfaz de producción.

## Reglas técnicas
1. Primero inventariar componentes DOM, IDs, eventos, contratos y CSS de la ruta activa.
2. Implementar una nueva capa visual aislada y con una sola hoja de estilos propia; no agregar !important encima de V5.
3. No duplicar mapas, geocodificadores, cálculos, reservas ni endpoints.
4. Probar flujos de origen/destino, parada, historial, cambio horario, disponibilidad, recalculo y envío único.
5. Comparar captura renderizada en Samsung con referencia aprobada; validar caché y commit publicado.
6. No fusionar ni publicar hasta pasar pruebas funcionales y visuales.
