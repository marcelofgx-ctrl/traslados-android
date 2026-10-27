# Quick Trip: contrato de consolidación y bloqueo de regresiones (26/10/2026)

No confundir: CI de mocks aprobado != ETA real del conductor validada.
No existe garantía absoluta de cero fallos; las versiones con defectos conocidos no se entregan.

## Reglas operativas
1. Mantener la rama QA actual, main y producción intocados. No modificar
   Supabase real, los motores GPS/reservas/rutas/ETA, Android ni Worker/D1.
   El guard de alcance del workflow y SHA fijados impiden cambios fuera de lista.
2. Verificar en CI todos los contratos aprobados (visual 360/393/412,
   GPS con permisos concedido/denegado, caché PWA, rutas, precios, paradas,
   no reserva real). Incorporar tests de backend simulado del conductor
   con ETA correcta, no disponible, HTTP 403 y bloqueo de transporte (como el que produce CORS). El preflight OPTIONS real se comprueba aparte.
   Nunca inventar kilómetros o posición real cuando fallan las consultas.
3. ÚNICO dominio de liberación QA del conductor: leer qaOrigin desde
   docs/quicktrip-qa-approved-origin.json. Un dominio Preview aleatorio NO
   sirve aunque el contenido HTML y las pruebas locales sean correctos.
4. Antes de compartir una URL, verificar Vercel READY, SHA, archivos servidos,
   modo read-only y preflight OPTIONS real con exactamente ese origen usando
   el workflow existente por workflow_dispatch y su entrada qa_url.
   OPTIONS solo verifica autorización CORS, no prueba GPS ni ETA, no envía
   reservas ni requiere modificar Supabase. Si cualquier check falla: NO ENTREGAR.
5. La prueba del conductor real en Samsung debe observar disponibilidad,
   km y minutos hasta el origen (o documentar conductor no disponible);
   la ETA no es el trayecto del pasajero y no debe usarse el auto simulado.
6. Guardar SHA, URL, run CI, resultado de OPTIONS, captura de Samsung y
   pendientes. No fusionar o desplegar producción sin permiso explícito.

## Límite claro
La suite browser intercepta todo servicio externo; confirma el comportamiento
de la aplicación pero NO la autenticidad de un dato del conductor. La prueba
final física y la conectividad del backend requieren evidencia aparte.
