# Mapa QT R25.31 — primera integración real de pantalla Conductor V0
22/10/2026. Desde la versión Mapa QT R25.30 firmada que el usuario confirmó que instaló bien.

- Se mantiene el mismo `applicationId=uy.com.mapatrayectos`, el mismo certificado de firma release y la misma Activity de Mapa. Solo se agrega el panel lateral `ConductorV0Navigator` usando gesto lateral.
- `versionCode=84`, `versionName=MapaQT-0.1-R25.31`.
- Se conservan GPS, trayectos, jornadas, reservas, buscador, MainActivity salvo el registro del panel lateral y manejo de Back.
- Tarjeta de Conductor **EJEMPLO VISUAL** y contadores ficticios (02/01/00). Aceptar/Rechazar deshabilitados porque no se configuró una conexión segura real. No se hacen operaciones Supabase desde el panel V0.
- No se construye ninguna APK adicional; no cambia `conductor/` ni web. No integrar a main. No desplegar producción.
- Prueba: deslizar desde el borde derecho hacia izquierda para abrir Conductor y a la derecha para cerrar. El APK requiere validación física real posterior; CI verifica firma y compilación pero no navegación táctil en Samsung.
