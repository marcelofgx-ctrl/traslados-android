# Traslados · Próximo servicio: mensajería y paquetería

**Estado:** diseño funcional, aún no habilitado a pasajeros ni integrado al backend. 10/10/2026. 
**Principio:** reutilizar el mismo ecosistema (PWA Cliente v31, APK Cliente Premium, Mapa Trayectos, backend operativo, motor de rutas), pero separar las solicitudes de pasajeros y paquetes para no mezclar estados, precios ni responsabilidad.

## Experiencia del pasajero/remitente
- Selector inicial claro: **Trasladar personas** (actual) / **Enviar paquete** (próxima fase; no mostrar como disponible hasta completarla).
- Origen de retiro y destino de entrega por geocodificador Uruguay; opcionales paradas, número y detalles del acceso.
- Categorías orientativas: **Documentos**, **Paquete pequeño**, **Paquete mediano**, **Otro (consultar)**; peso, medidas, fragilidad y observaciones.
- Contactos de quien envía y quien recibe, teléfonos con validación; proteger información de terceros. Contactar por WhatsApp solo a pedido explícito.
- Retiro **Ahora / En 10 min / Programar** usa disponibilidad actual del conductor y ETA conductor→retiro. El valor de entrega se calcula por separado por recorrido origen→destino.
- Mostrar estimación de precio como **referencia, a confirmar por el conductor**, sin asumir que las tarifas de pasajeros aplican a paquetería.
- Antes de enviar, resumen: dirección de retiro, entrega, descripción no sensible, personas de contacto, km, tiempo y presupuesto orientativo.
- Aceptación del presupuesto por remitente, seguimiento sin exponer la ubicación exacta del conductor, instrucciones de entrega y comprobante privado.
- Señalar límites de dimensiones/carga según capacidad del vehículo y cobertura del servicio; artículos restringidos no se ofrecen como entregas normales.

## Aplicación conductor
- Bandejas separadas **Viajes** y **Envíos** con sus contadores y filtros, mismas notificaciones premium, diferentes iconos/acciones.
- Estados: SOLICITADO → PRESUPUESTADO → ACEPTADO → EN_RETIRO → RETIRADO → EN_CAMINO → ENTREGADO; incidencias/cancelaciones registradas. Nada de «entregado» sin confirmación explícita.
- Mostrar remitente y destinatario solo al conductor autorizado y por la duración necesaria; navegación y llamada/WhatsApp voluntaria.
- Entrega: confirmar nombre de receptor y opcional prueba de entrega segura; foto o firma solo con consentimiento y acceso protegido, sin publicar a terceros.

## Base, API y seguridad
- Añadir modelo específico `shipments` con claves de cliente, origin/destination con coordenadas, pesos/categorías, estado, presupuesto, contactos y timestamps; `shipment_events` para auditoría.
- RPC autenticadas separadas y operaciones idempotentes; invitados sujetos a controles antiabusos.
- Reutilizar geocodificador IDE/POIs, motor vial Cloudflare/ORS, disponibilidad del conductor y cotización adaptable; conservar lógica de consentimiento GPS y no exponer coordenadas precisas públicamente.
- Nunca insertar datos de paquetería en tablas de viaje con significado confuso ni sobrescribir estados de reservas existentes.
- Documentar protección de datos, seguro, responsabilidad de transporte de mercaderías y requisitos normativos uruguayos antes de apertura comercial.
- Pasarelas, pago electrónico, verificación SMS/OTP y comprobantes formales son fases explícitas; no aparentar que están activos.

## Entregas por fase
1. Diseño premium de la opción «Envíos», validación de campos y recorrido: prototipo sin envíos reales.
2. Migraciones SQL aditivas, RPC y cálculo de presupuesto separado.
3. Flujo Cliente→Conductor→Cliente con estados verificados y notificaciones, pruebas E2E de distintos escenarios.
4. Seguridad, políticas, comprobante de entrega, aceptación/operación real; publicación PWA y APK.
5. Prueba piloto con envíos manualmente autorizados, métricas y refinamiento.

## Regresiones obligatorias
- Mantener intactos los traslados existentes, clientes y viajes históricos.
- Nunca generar una reserva/cobro automáticamente cuando se consulta «Ahora».
- Casos de prueba: dirección no encontrada, teléfono inválido, no disponible, sin precio, paquete prohibido, rechazo, cancelación, destinatario ausente, fallas de red y duplicados.
- Accesibilidad móvil, rendimiento bajo 4G, reintentos y tamaños reales de pantalla Android.
