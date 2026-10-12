# PWA v63 · Aviso de paquetería visible en pantalla móvil

**12/10/2026 · rama de revisión.** Corrección puntual de continuidad sobre PWA v62 publicada; no rediseña tarjetas ni altera rutas, presupuestos, disponibilidad, ETA, reservas, sesiones o chat.

## Problema reproducible por inspección del código
La tarjeta «Tu paquete» mostraba/ocultaba el aviso **debajo** del grupo de tres tarjetas. En una pantalla Samsung corta, el contenido podía abrirse por debajo del área visible sin indicación inmediata; el botón seguía siendo el foco del teclado.

## Ajuste
- Al abrir el aviso informativo, el navegador lo desplaza **solo lo necesario** con `scrollIntoView({block:"nearest"})`. Se usa desplazamiento sin animación si está activada la preferencia de movimiento reducido.
- El indicador final alterna **＋/−** de forma visual, mientras `aria-expanded` conserva su estado semántico. El cierre no mueve el scroll.
- Si el usuario pasa a «Tu viaje» o «Tu reserva», se repliega el aviso y se restablece **＋**, evitando un estado visual engañoso.
- Ninguna acción del modo «Tu paquete» genera reservas, mensajes, geocodificación ni llamadas al backend.

## Versiones y contratos
- **PWA v63**, `service-home.js?v=3`, consulta de `app.js?v=63` y `styles.css?v=63` (fuentes operativas sin cambios), y service worker `traslados-cliente-pwa-v63`; Los archivos JS y CSS principales conservan íntegramente su código; solo se renuevan sus parámetros de caché por compatibilidad con verificadores y WebView.
- Cliente Premium **v15 WebView**, Mapa Trayectos **R25.0**, Conductor **11.5-R5**, backend y credenciales **sin cambios**. No recompilar APK firmadas.
- Tests del selector y continuidad artística v62 actualizados; todo el pipeline web existente debe quedar verde antes de publicar.

## Comprobaciones pendientes en Samsung físico
1. Abrir Inicio en Chrome y APK Cliente Premium v15; verificar círculos y legibilidad en modo vertical.
2. Tocar «Tu paquete» cerca del borde inferior: el texto informativo debe aparecer en pantalla sin desplazamiento lateral y sin crear solicitudes. Volver a tocar: cerrar sin salto de scroll.
3. Con mensaje abierto, tocar «Tu viaje» y regresar a Inicio: el indicador del paquete debe volver a **＋**.
4. Probar tamaño de fuente aumentado y ajuste **Quitar animaciones**; la aparición debe respetar movimiento reducido.
5. «Tu viaje» y «Tu reserva»: controlar que el formulario conserva direcciones al volver; luego Mis traslados y chat (activo o archivado) de una reserva previamente autorizada.
6. No interpretar pruebas CI/Android DEBUG como prueba real de tacto, recepción push ni ejecución con el Cliente cerrado. FCM sigue pendiente.

## Reversión
Revertir el PR correspondiente a v63 en `main`; el servicio informativo vuelve al modo v62 y las reservas/aplicaciones firmadas siguen intactas. Nunca vaciar datos de producción para esta prueba.
