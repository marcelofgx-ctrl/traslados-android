# Horarios habituales, solicitudes y agenda — Traslados PWA v68 / Conductor R6

**Publicación 13/10/2026.** PWA en `main` / Pages; APK Conductor 11.5-R6 firmada y descargable sin quitar R5. La APK Cliente v15 usa PWA v68 vía WebView.

## Política
- **Programadas:** fecha y hora futuras elegidas por el pasajero. Por defecto se **pueden solicitar 24 horas, todos los días**, aunque el horario habitual sea 08:00–22:00. Se registran como `PENDIENTE`, sin promesa de plaza confirmada. Un conductor debe abrirla y enviar el presupuesto; el pasajero debe aceptar para confirmar. Confirmación vuelve a comprobar conflictos, márgenes, traslados y bloqueos, pero NO aplica un veto por horario habitual.
- **Inmediatas:** disponibilidad pública según jornada activa, GPS preciso/fresco, estado libre y ausencia de conflictos. Se permite salir de horario habitual si la opción está activada; no hay conductor “disponible” ficticio por tener habilitada la opción.
- **Conductor → Horarios** (requiere R6) mantiene días, horas, márgenes y dos preferencias independientes: programadas todo el día y recogidas inmediatas fuera del horario habitual cuando está conectado. El interruptor superior pausa solo pedidos programados.
- **Servidor:** nuevo setter `driver_set_availability_settings_v12`, antiguo R5 compatible, políticas por trigger de reserva programada afectan tanto a invitado como registrado. El cálculo `customer_check_availability_v11_4` conserva `available` como capacidad de un turno y añade `requestable` como permiso de SOLICITAR (conceptos distintos).
- **Paradas:** Conductor R6 presenta todas las paradas de la reserva en orden y las incorpora como waypoints al abrir «RUTA». Para presupuestar se debe considerar el trayecto completo; no confiar en kilómetros directos entre origen y destino.
- **Vencimientos:** dos RPC de presupuesto y `customer_quote_decision_v13` impiden enviar/aceptar presupuesto programado cuando ya pasó la hora. Los inmediatos mantienen los 90 minutos.
- **Seguridad:** sin nuevos tokens expuestos, no se cambió la firma de la APK ni la de la antigua RPC, y no hay confirmaciones sin doble decisión conductor/pasajero.

## Validaciones
- [Backend políticas y paradas (PR #15)](https://github.com/marcelofgx-ctrl/traslados-web/pull/15) — migración aplicada.
- [Guard de vencimiento (PR #16)](https://github.com/marcelofgx-ctrl/traslados-web/pull/16) — aplicado y verificado por código SQL y prueba transaccional.
- [PWA v68 / Conductor R6 (PR #69)](https://github.com/marcelofgx-ctrl/traslados-android/pull/69). [PWA/Pages OK](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38352225592), [Conductor firmado OK](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38352100436).
- Tests de inserción invitada fuera de franja, bandera restrictiva, presupuesto explícito fuera de horario, expiración. Todos con transacción `ROLLBACK` y sin registros QA remanentes.

## Pendientes y límites de la validación
1. Instalar Conductor R6 en Samsung, entrar a Horarios, guardar valores y comprobar que sobreviven reinicios; verificar dos opciones en vivo y fuera de franja.
2. Crear una solicitud programada real de control y completarla desde Conductor y Cliente para validar sonido, lista, presupuesto y aceptación; no se ejecutó flujo físico bidireccional.
3. Solicitudes sin km/min persistentes: priorizar modelo explícito de estimación vial verificada, sin permitir falsificar el valor del conductor; no solucionado por esta versión. Registro finalizado histórico con km=0 sigue siendo dato histórico, no alterar silenciosamente.
4. Push FCM con Cliente completamente cerrado sigue pendiente.
5. Mantener R5 y Mapa R25.0 estables; la nueva UI de Horarios requiere R6.

**Descarga R6:** https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/downloads/traslados-conductor-r6.apk

**Importante:** ésta es una mejora de la versión PWA v68 en producción; la etiqueta global `1.0.0` se reserva para después de QA física y cierre de incidencias.
