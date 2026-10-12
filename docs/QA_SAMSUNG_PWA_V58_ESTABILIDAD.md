# QA Samsung — PWA v58 y estabilidad de interacción (11/10/2026)

**Sitio único:** https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/
**Producción actual:** PWA v58 fusionada mediante PR #58 en main `a6d4c89`; Pages run 38191118475 y PWA CI run 38191119097: success.
**Cliente:** APK Premium v15 WebView del mismo sitio; prueba de paridad Android DEBUG en PR v58: run 38191040498, success.
**Estado:** QA visual y punta a punta en Samsung **PENDIENTE**. No confundir automatización con interacción física.

## Evidencia existente y limitaciones
- Capturas aportadas del 11/10 anteriores a v58: viaje finalizado por UYU 890 con dirección y monto duplicados, filtros largos, historial desplegado y chat archivado con composición deshabilitada. Motivaron v58, **no validan visualmente v58**.
- Smoke `guided-passenger-flow-v58-smoke.cjs`: confirma estructura, RPC conservadas, privacidad de chat, textos largos y opciones plegadas con mocks y comprobaciones estáticas. No simula WebView, tacto ni backend operativo.
- La PWA refresca `Mis traslados` cada 20 s si está visible. La rama de revisión `fix/pwa-v58-samsung-stable-refresh` contiene, sin desplegar, protección contra respuestas de una sesión anterior, reducción de reconstrucciones de tarjetas sin novedades y persistencia de detalles interiores cuando sí hay novedades. Validar CI/PR **antes de integrar a main**.
- El chat finalizado es de solo lectura; el popup/sonido/vibración son solo primer plano y optativos. Push real con app cerrada requiere FCM y **no** está implementado.
- No modificar datos operativos ni hacer reserva artificial sin solicitud explícita del usuario.

## Prueba 1 — Navegación y composición (sin crear reservas)
1. En Samsung Chrome abrir el enlace oficial y forzar recarga normal. En APK Cliente Premium v15 abrir la misma web.
2. En **Reservar**, elegir una dirección de origen completa y destino **Aeropuerto Internacional de Carrasco**; confirmar que los textos no tienen puntos suspensivos al plegar. Km, minutos y referencia de costo **solo** si motor vial respondió; no confundir ETA de conductor.
3. Cambiar «Ahora», «En 10 min», «Programar» sin pulsar **Enviar solicitud**. Comprobar resumen correcto, sin totales viejos al editar una parada, y sin scroll lateral a 360–412 px.
4. Navegar **Mis traslados**: una sola fila fija de pestañas, sin cabecera superpuesta; Próximos, Responder, Historial; filtros y aviso de sonido/vibración ocultos bajo «Más filtros y avisos».
5. En las reservas existentes, comprobar una única aparición del importe en la tarjeta principal y nombre de origen y destino completos. Los detalles de datos e historial están plegados inicialmente.

## Prueba 2 — Estabilidad del refresco (sin mutar backend)
1. Abrir «Historial», expandir un viaje finalizado y desplegar «Historial y etapas del viaje». Mantener esa lectura más de 40 segundos con la pantalla encendida.
2. La vista y el scroll NO deberían saltar ni cerrarse con los refrescos de 20 s. La rama de estabilización aborda el riesgo detectado en el código base v58; antes de integrarla, registrar si el fallo es reproducible en producción.
3. Abrir «Ver mensajes archivados». Confirmar que no existe textarea ni botón «Enviar», que se pueden consultar mensajes anteriores, y que cerrar/Escape vuelve a la ficha.
4. Cerrar y reabrir mensajes; no deben aparecer datos de otra conversación. Probar teclado Android: el contenido no queda oculto tras el teclado en chat activo si existe uno autorizado.
5. Iniciar y cerrar sesión **solo con cuentas propias**; un cambio de cuenta no debe dejar visible el historial ni el chat previo. No compartir capturas con tokens, números privados o datos de terceros.

## Prueba 3 — Flujo conductor↔pasajero (requiere autorización separada para viaje real)
Solo con una reserva real autorizada: solicitud registrada ≠ presupuesto enviado ≠ aceptación del pasajero ≠ conductor en camino. Confirmar mensaje contextual y código, presupuesto definitivo único, respuesta confirmada por RPC, mensajería ida/vuelta, cambios de fase en Conductor/Mapa, finalizado en historial, sin mensajes editables al cerrar. Nunca afirmar ETA o GPS en tiempo real sin presencia/servicios vigentes.

## Matriz de estado (11/10)
| Comprobación | Resultado |
|---|---|
| PR v58 fusionado en main | PASS |
| Pages v58 SHA main | PASS |
| Smoke JS y revisión UI v58 | PASS |
| APK Cliente v15 Android DEBUG parity | PASS |
| Samsung Chrome v58 | PENDIENTE |
| Samsung APK WebView v15 cargando v58 | PENDIENTE |
| Sin overflow/títulos cubiertos | PENDIENTE |
| Refresco sin cerrar detalles ni saltar | PENDIENTE |
| Chat archivado/activo en teléfono | PENDIENTE |
| Viaje autorizado completo con Conductor + Mapa | PENDIENTE |
| FCM push app cerrada | NO IMPLEMENTADO |

## Criterio de avance
No etiquetar como «QA físico completado» hasta disponer de evidencia Samsung. Registrar para cada fallo: versión visible, Chrome o WebView, hora UYT, pantalla, secuencia reproducible y captura sin identificadores privados. Primero cerrar regresiones; luego, UX post-solicitud y seguimiento contextual; push FCM y paquetería son proyectos posteriores. Conservar rollback a main/PWA v58 hasta validar nuevos cambios.
