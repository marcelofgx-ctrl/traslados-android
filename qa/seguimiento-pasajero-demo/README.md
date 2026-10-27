# Quick Trip — demostración aislada Android

Esta carpeta es un **prototipo con datos simulados** para ver el mapa del pasajero desde la recogida hasta destino.

- No comparte GPS real, no se comunica con Mapa QT, ni toca Supabase/producción.
- No solicitar permisos de ubicación. La ruta ilustrativa no es una ruta vial validada.
- Única APK oficial del proyecto permanece **Mapa QT con Conductor integrado**. Esta APK debug temporal **no es Cliente oficial**.
- Build: `cd qa/seguimiento-pasajero-demo && gradle :app:assembleDebug`.
- Resultado: `app/build/outputs/apk/debug/app-debug.apk`. Instalación manual y prueba Samsung pendientes.
- Para producción, alimentar la PWA autorizada con posición GPS de la reserva y rutas reales existentes, sin segunda APK.
