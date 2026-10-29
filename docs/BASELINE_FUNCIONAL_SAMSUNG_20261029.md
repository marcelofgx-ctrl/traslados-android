# Quick Trip — Línea base funcional F1 / Samsung / 29-10-2026

Objetivo: mantener como contratos los progresos demostrados, separar mejoras estéticas de motores y NO declarar GPS real funcional sin verlo en el teléfono.

## Versiones

- Pasajero QA Web/PWA: bdcc0925021c6888f966e849fa029a62d1fdfcf0; Vercel dpl_CDnABi5pRX135QfMX6BeAxKTztSE, READY, target=null.
- Mapa QT Android físico: MapaQT-0.1-R25.38, confirmada por Samsung; rama fuente R25.38 SHA 38bc41349a9e3a799b54652e080ad867df60f3db. El hash del APK instalado no fue comprobado.
- Edge pickup-eta activo: v9, verify_jwt=false; fuente traslados-web@f3429b329d0ccb23754c9d3d704b2b7be2774d35, CORS preflight y sesiones inválidas probadas con CI HTTP #40811387511. Rollback exacto v8: traslados-web@e03d764a7b7f34d92555c2b5e19c5a13b9ff1dcd.
- Main Android/Web histórica: 53dd427d5bda65e84f585b4db2c565227aff673c. No es idéntica a la PWA QA validada.

## Samsung: funcionalidad observada

1. Origen/destino, ruta real por calles y precio consistente: ejemplo 3,1 km, 7 min y 124 UYU.
2. Estimación PRE-RESERVA restablecida: conductor disponible, 0,5 km, hasta 5 min; no implica GPS preciso compartido.
3. Pasajero y Conductor muestran una reserva aceptada e iniciada; se observaron los cambios: aceptado → en camino → en viaje, con importe estable.
4. La pantalla canónica de seguimiento tiene origen/destino arriba, mapa grande y solapa inferior compacta/expandible; ruta blanca dibujada antes de comenzar el viaje.
5. Gestos, zoom y recentrado fueron aprobados en pruebas Samsung anteriores.

## Fallos abiertos que NO deben marcarse SUCCESS

P0 — Posición real del coche por reserva no validada: aparecía «Posición del vehículo pendiente», sin icono GPS verificado ni ruta coche→recogida. Falta auditar publicación privada, consentimiento, sesión, frescura e identidad de la reserva.
P0 — Mapa vacío al entrar en «Viaje en curso»: captura 00:24 sin cartografía/trazado aunque encabezado y solapa existían. Causa no establecida.
P1 — Etiqueta «GPS REAL» aparece incluso sin datos del coche; evitar esa afirmación falsa.
OPERATIVA — Conductor indicó tres servicios sin cerrar; pueden afectar disponibilidad. No cerrar ni borrar reservas desde CI.

No guardar nombres, direcciones exactas, teléfonos, matrículas, códigos de reservas ni capturas físicas en repositorios. Las fotos originales son evidencia del chat.

## Contratos a proteger

- Ruta calculada por calles y tarifa fija sin recálculo silencioso. Distinguir tiempo del viaje de ETA al origen.
- Disponibilidad pública y ETA aproximada de invitado sin entregar GPS preciso; verificación de CORS y privacidad.
- Reserva única; transiciones válidas; aceptación del conductor; precio/origen/destino estables; sin falsos avisos.
- GPS privado solo con posición reciente y autorizada en una reserva real. No mostrar automóvil ficticio.
- Cámara, zoom, solapa y controles se mantienen entre fases; trazado real desaparecido es fallo.
- Superficies: única APK Mapa QT con Conductor integrado y única Web/PWA Pasajero. Sin APK independientes.
- Diseño visual libre de mejorar siempre que sus regresiones pasen.

## Procedimiento F1

- Los cuatro motores congelados por sus hashes de Git: route-engine.js, eta-engine.js, booking-contracts.js y pickup-cadence.js. Los cambios deliberados requieren aprobación y una nueva línea base; no actualizar hashes para ocultar una regresión.
- Ejecutar suite QA local sin red real ni reservas reales: motor vial, tarifa fija, booking, presencia, privacidad GPS y transición de cinco etapas en navegador móvil simulado a 390/412 px. Preservar capturas y JSON del CI.
- Distinguir verde de CI MOCK de aprobado físico en Samsung. GPS privado y mapa al iniciar viaje requieren prueba física.
- QA isolada solamente. Sin deploy, merge, cambios de Supabase o APK por este lote.
- Las reglas GitHub de branch protection/required checks no se han habilitado mediante esta rama. En futuros lotes se deberá partir del SHA F1 e incorporar la suite; no afirmar protección obligatoria de main sin ruleset.

CI de referencia: seguimiento simulado #40778978599; Edge QA #40809843793; Edge v9 HTTP real #40811387511.

Nota técnica (29/10): el test histórico fixed-fare-v1-smoke.cjs sigue exigiendo un PIN de Conductor ya eliminado de la arquitectura. No se usa como puerta F1. En su lugar se ejecuta el contrato puro vigente fixedFarePickup, validando 3,1 km, 7 min, UYU 124 y rechazo de tarifa manipulada. Se conserva el test antiguo por trazabilidad, sin deshabilitar otras pruebas.
