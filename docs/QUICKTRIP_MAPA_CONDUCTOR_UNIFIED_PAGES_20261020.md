# Mapa + Conductor — dos pantallas, una aplicación (QA 20/10/2026)

## Comportamiento

- Producto único: Mapa Trayectos instala Conductor dentro de la misma APK y comparte los datos privados de la aplicación. **No publicar ni mantener una APK Conductor separada.**
- Mapa ocupa la pantalla principal. La pestaña lateral **Conductor** puede tocarse o abrirse deslizando hacia la izquierda desde una franja interior del borde derecho del mapa. Los arrastres normales del mapa, el gesto de Android y el deslizador de jornada quedan preservados.
- Conductor está a la derecha. Volver a Mapa: deslizar a la derecha desde la franja interior izquierda o usar el botón «Mapa». Es una transición interna, no otro icono de aplicación.
- Conductor **no muestra formulario de PIN cuando está integrado**. Si falta vincular esta instalación, muestra «Vincular desde Mapa», nunca un acceso separado.
- La autorización inicial para esta instalación está en Mapa → Menú → **Conductor y recogidas**. En una instalación nueva pide una sola vinculación de conductor y permiso explícito para publicar disponibilidad GPS solo durante jornada. La autenticación de RPC en Supabase sigue activa; **no convertir estas RPC en anónimas**.
- Luego de vincular, el cambio de estado es automático: iniciar jornada + GPS válido + sin pausa/viaje/ocupado y sin bloqueo de agenda → candidato disponible. Cerrar jornada, pausar, iniciar viaje o marcar ocupado bloquea la recogida. El backend conserva las comprobaciones de GPS/heartbeat menores a 90 segundos y de agenda.
- La ubicación exacta nunca se muestra al pasajero: solo distancia vial y tiempo autorizados. Si falta GPS o la red falla se informa estado desconocido/no disponible, no se inventan kilómetros.

## Identidad de las QA

Las APK QA previas generan un `applicationId` nuevo por cada compilación. Android las trata como instalaciones diferentes. Por eso **no comparten** datos privados, credenciales, vinculación ni autorización GPS. Esta característica causa el caso «jornada abierta en Mapa QA pero conductor no localizable» observado el 20/10/2026 en Supabase. El proyecto no debe reactivar remotamente el consentimiento ni copiar secretos de otra instalación para ocultar el problema.

Para próximas versiones que deban conservar el vínculo, distribuir actualizaciones con el **mismo package id, firma estable y versionCode creciente**, usando firma segura en CI; nunca exportar claves a archivos de descarga ni saltarse autorizaciones.

## Pruebas de aceptación Samsung pendientes

1. Instalar APK de integración QA correctamente firmada. Autorizar acceso al conductor **solo desde Mapa** y verificar que el menú reconozca la vinculación.
2. Play / ubicación precisa / jornada sin viaje: comprobar `driver_live_presence` habilitado, con posición, `received_at` reciente; consultar `public_driver_availability_v1` y luego recogida y ETA desde la Web.
3. Deslizar Mapa → Conductor → Mapa con animación. El mapa debe poder desplazarse y el deslizador de jornada nunca activarse por un gesto horizontal del borde.
4. Pausa, viaje activo, Stop, GPS desactualizado o pérdida de conexión: la web deja de indicar disponible.
5. Nueva sesión o reinicio del mismo paquete: vinculación conservada sin mostrar PIN en Conductor.

La prueba de compilación Android no demuestra que la recogida funcione realmente. No modificar tarifas, rutas, reservas ni el historial para estas pruebas.
