# Quick Trip Conductor C1 — Solapa premium QA

Base: Android Mapa QT R25.38-P0.1 QA SHA b6ee0a56bfb8e3f1aad7f47804a48d4ab7cf369f.
Referencia visual: mock aprobado de mapa completo y panel inferior. Exclusivamente QA.

## Entrega
- Misma Activity y MapLibre, sin APK Conductor nueva; una solapa inferior plegable tras aceptación CONFIRMADA por el servidor.
- Nombre del pasajero, precio, recogida, destino, km y duración desde el snapshot autenticado de TransferAlerts, fresco (<=45 s) y enlazado al ID correcto.
- Una acción principal por estado de la reserva: Ir a recoger / Llegué al origen / Iniciar viaje / Finalizar viaje. Todas delegan al controlador original.
- Rechazos o estados no verificados no abren una solicitud ficticia; con sincronización vencida, acción deshabilitada.
- El panel de Conductor existente permanece accesible por transición lateral.
- Botón Ruta: abre la navegación por Google Maps usando el método existente; NO añade trazado inventado dentro de MapLibre.
- No presenta rating, bonus, método de pago o ETA de recogida ficticios.

## Estados y control
ACEPTADA+SIN_INICIAR → EN_CAMINO; ACEPTADA+EN_CAMINO → EN_PUNTO; ACEPTADA+EN_PUNTO → onStartTrip; EN_VIAJE → onFinishTrip.
Variantes de estado no implementadas por la RPC actual: ninguna acción. El controlador revalida las operaciones.

## Pendientes antes de entregar APK
Prueba Samsung de transiciones, mapa visible con solapa, adaptación al teclado y gestos, estados asincrónicos, cancelación, app en segundo plano y navegación real.
La ruta dibujada por calles en el mapa nativo necesita una fuente vial verificada; no forma parte del lote C1.
Solicitud entrante sobre el mapa (antes de aceptar) todavía utiliza el panel conductor tradicional; se puede mejorar en C2.

## Garantías
No modificar GPS, Supabase, reservas, notificaciones, tarifas, main, APK instalada o Web/PWA F1.
No compilar APK ni desplegar en producción. Compilación de clases en CI únicamente, tests offline.
Rollback directo al SHA Android base. El CI verde NO equivale a validación visual física.
