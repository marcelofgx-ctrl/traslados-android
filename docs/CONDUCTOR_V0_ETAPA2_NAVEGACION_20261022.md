# CONDUCTOR V0 — etapa 2: navegación aislada (22/10/2026)

## Permiso exacto
Aprobación del usuario exclusivamente para desarrollar navegación interna Mapa ↔ Conductor V0 en rama aislada y ejecutar pruebas de código. NO autorización para merge, APK, despliegue, producción, Supabase ni modificaciones a la instalación existente.

- Base visual aprobada: `feat/conductor-v0-limpio-20261021` @ `499fd8d58ee4bddde29f428a078baa1549a5b35f`.
- Rama exclusiva: `feat/conductor-v0-etapa2-navegacion-20261022`.
- Única APK oficial sigue siendo Mapa Trayectos; esta etapa no genera ninguna.

## Comportamiento implementado
- En el mapa, un gesto desde una zona estrecha del borde derecho hacia la izquierda abre el panel nativo aprobado, dentro de la MISMA MainActivity.
- El menú Quick Trip · Conductor V0 proporciona acceso alternativo (accesibilidad y conflictos con el gesto Back de Android).
- En Conductor, deslizar a la derecha o utilizar Atrás cierra el panel y devuelve a Mapa sin reconstruir MapView.
- El fondo sigue vivo: no se pausan jornada, GPS, ruta, notificaciones, servicios ni ubicación; la navegación manipula solo vistas.
- La cabecera negra restaura colores de barras Android en el retorno a Mapa.
- No se conectan peticiones: sin listener, contadores desconocidos, solicitud nula, ninguna llamada RPC; la ausencia de datos no significa ausencia real de solicitudes.
- El código heredado continúa disponible internamente por protección: no se eliminan clases, servicios, PIN de consentimiento de recogida, migraciones ni contratos de reservas.

## Exclusiones y riesgos
- No se valida navegación real en teléfono ni emulador en esta etapa porque no se permite generar APK.
- El gesto por borde puede competir con el gesto Atrás del sistema Android; menú de Mapa actúa como alternativa. Solo test físico futuro puede verificarlo.
- `TransferQuickPanel`, `TrackingService` y avisos pueden seguir dirigiendo al Conductor legado; se dejan intactos expresamente por dependencia de reservas y monitor. NO afirmar migración completa de todos los accesos.
- La pantalla V0 todavía no muestra peticiones reales y por eso NO reemplaza la gestión comercial actual.
- No hay desactivación ni limpieza de servicios antiguos en etapa 2. Requiere inventario/pruebas y autorización separada antes de hacer esa sustitución.
- No se alteran `main`, `conductor/`, `cliente/`, `core-reservations/`, backend, Play/Stop, GPS ni datos.
- Compilación permitida: únicamente clases Java; tareas `assemble*`, `bundle*` y `package*` prohibidas.

## Validación
GitHub Actions `.github/workflows/conductor-v0-etapa2-code-qa.yml` comprueba lista blanca de archivos, casos de gestos, compilación Java sin empaquetar APK y ausencia de modificaciones en servicios/motores. **Resultados por registrar desde ejecución de rama**.

## Puerta de aprobación
Antes de integrar, fusionar o producir una APK, presentar cambios concretos, CI, limitaciones y pedir autorización nueva.
