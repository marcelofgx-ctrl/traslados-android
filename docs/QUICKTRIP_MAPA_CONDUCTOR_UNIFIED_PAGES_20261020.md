# Mapa + Conductor — dos pantallas, una aplicación (QA 20/10/2026)

## Comportamiento

- Producto único: Mapa Trayectos instala Conductor dentro de la misma APK y comparte los datos privados de la aplicación. **No publicar ni mantener una APK Conductor separada.**
- Mapa sigue siendo la única `Activity` operativa: el nuevo `UnifiedDriverPanel` es una vista de pantalla completa superpuesta en el mismo `FrameLayout` y animada horizontalmente. **Nunca usar `startActivity` para navegar entre Mapa y Conductor.**
- Ya NO existe la antigua pestaña lateral flotante grande. La entrada es deslizar hacia la izquierda desde la franja interior derecha del mapa o tocar el pequeño chevrón en el título de la cabecera. El mapa, su GPS y la jornada permanecen activos debajo.
- Volver a Mapa: deslizar a la derecha desde la franja interior izquierda o tocar la flecha de retorno del panel. Al abrir el panel debe tener mayor elevación gráfica que TODOS los controles elevados del mapa; al cerrar no queda ninguna capa fantasma.
- Conductor muestra su cabecera, tarjeta de jornada, contadores, solicitudes y acceso al historial incluso cuando el dispositivo todavía no se vinculó. No inventar reservas ni dibujar una pantalla vacía; mostrar estado de vinculación honesto.
- Conductor **no muestra formulario de PIN**. Si falta vincular la instalación, lo explica dentro del panel con tarjetas de estado. Play tampoco abre un diálogo PIN de forma automática: la autorización GPS comercial se gestiona solo desde Menú de Mapa por decisión explícita.
- La autorización inicial para esta instalación está en Mapa → Menú → **Conductor y recogidas**. En una instalación nueva pide una sola vinculación de conductor y permiso explícito para publicar disponibilidad GPS solo durante jornada. La autenticación de RPC en Supabase sigue activa; **no convertir estas RPC en anónimas**.
- Luego de vincular, el cambio de estado es automático: iniciar jornada + GPS válido + sin pausa/viaje/ocupado y sin bloqueo de agenda → candidato disponible. Cerrar jornada, pausar, iniciar viaje o marcar ocupado bloquea la recogida. El backend conserva las comprobaciones de GPS/heartbeat menores a 90 segundos y de agenda.
- La ubicación exacta nunca se muestra al pasajero: solo distancia vial y tiempo autorizados. Si falta GPS o la red falla se informa estado desconocido/no disponible, no se inventan kilómetros.

## Identidad de las QA

Las APK QA previas generan un `applicationId` nuevo por cada compilación. Android las trata como instalaciones diferentes. Por eso **no comparten** datos privados, credenciales, vinculación ni autorización GPS. Esta característica causa el caso «jornada abierta en Mapa QA pero conductor no localizable» observado el 20/10/2026 en Supabase. El proyecto no debe reactivar remotamente el consentimiento ni copiar secretos de otra instalación para ocultar el problema.

Para próximas versiones que deban conservar el vínculo, distribuir actualizaciones con el **mismo package id, firma estable y versionCode creciente**, usando firma segura en CI; nunca exportar claves a archivos de descarga ni saltarse autorizaciones.

## Pruebas de aceptación Samsung pendientes

1. Instalar APK de integración QA correctamente firmada. Autorizar acceso al conductor **solo desde Mapa** y verificar que el menú reconozca la vinculación.
2. Play / ubicación precisa / jornada sin viaje: comprobar `driver_live_presence` habilitado, con posición, `received_at` reciente; consultar `public_driver_availability_v1` y luego recogida y ETA desde la Web.
3. Capturar la pantalla Conductor en emulador y Samsung para verificar: nada de controles del mapa por encima, fondo petróleo/champagne, tarjeta y pestañas con contenido incluso sin vinculación, y retorno al mapa deslizando. Validar que el deslizamiento horizontal no afecte la manipulación del mapa ni el deslizador de jornada.
4. Pausa, viaje activo, Stop, GPS desactualizado o pérdida de conexión: la web deja de indicar disponible.
5. Nueva sesión o reinicio del mismo paquete: vinculación conservada sin mostrar PIN en Conductor.

La prueba de compilación Android no demuestra que la recogida funcione realmente. No modificar tarifas, rutas, reservas ni el historial para estas pruebas.

## Regresión visual detectada y corregida (21/10/2026)

El primer panel embebido fue capturado en QA visual con la cabecera, velocímetro, brújula y panel de jornada del mapa dibujados **por encima de Conductor**: son vistas con `elevation` elevada, y `bringToFront()` no es suficiente por sí solo. Corrección aislada: `driverPanel.setElevation(dp(48))` y cambios de barras de sistema al abrir/cerrar, conservando su estado anterior. `mapatrayectos/scripts/mapa-visual-capture-with-panel.sh` captura Mapa, Conductor y regreso con el deslizamiento, y la prueba visual controla que no sea una superficie vacía.

**Estado de entrega:** no declarar QA visual aprobada ni publicar como oficial sin revisar la captura posterior a la corrección y la prueba física; el backend de reservas aún requiere vinculación segura desde Mapa.
