# QA P0.1 — Diagnóstico seguro del GPS privado en Mapa QT R25.38

**Fecha:** 29/10/2026. **Base de código Android:** rama \`qa/mapa-qt-r2538-firma-aislada-20261024\`, SHA \`38bc41349a9e3a799b54652e080ad867df60f3db\`. **Línea base funcional F1 Web/PWA:** SHA \`98049035ea227de3b10d659834c94c39e1e779d3\`, **sin modificar ni fusionar**.

## Contexto confirmado

- Mapa QT R25.38 comparte presencia pública mediante \`mapa_presence_ping_v1\` y, después de un \`ok\` público y condiciones de ubicación/consentimiento válidas, intenta \`mapa_reservation_position_v1\` para GPS privado por reserva.
- El código anterior descartaba el JSON privado y silenciaba excepciones, por lo que el pasajero podía recibir disponibilidad/ETA previa sin posición del vehículo para una reserva concreta.
- La auditoría P0 en QA \`656d1a788194060d15f45b802c874b2ff2521e86\` probó los contratos SQL con datos sintéticos y encontró el retorno \`no_unique_active_reservation\` cuando no hay exactamente una reserva elegible globalmente.
- **No se ha demostrado que el teléfono real reciba ese motivo concreto**, ni que las tres reservas sin cerrar sean tres reservas elegibles para el GPS.

## Qué cambia (exclusivamente código QA)

1. **Clase nueva:** \`PrivateGpsDiagnostic.java\` — enum de estados finitos en memoria, sin almacenamiento, red, log o datos de usuario. Registra exclusivamente:
   - \`ACKNOWLEDGED\` para \`ok:true\`, sin afirmar que el pasajero realmente reciba GPS.
   - \`NO_UNIQUE_RESERVATION\`, \`UNAUTHORIZED\`, \`GPS_UNAVAILABLE\`, \`STALE_GPS\` para motivos SQL conocidos.
   - \`SERVER_REJECTED\` para cualquier otro motivo SQL, sin conservar su contenido.
   - \`PRIVATE_TRANSPORT_ERROR\`, \`PUBLIC_TRANSPORT_ERROR\`, \`PUBLIC_REJECTED\`, \`CONDITIONS_NOT_MET\` y \`NO_ATTEMPT\`.
2. **\`Api.java\`** mantiene la RPC, su payload, el control de presencia, la jornada y las condiciones del GPS **exactamente iguales**. Ahora interpreta \`ok/reason\` del JSON privado, clasifica sin guardar respuesta, y transforma excepciones en códigos locales **sin registrar texto ni stack traces**. El error privado sigue sin interrumpir el registro GPS ni la presencia pública.
3. **\`SystemStatusActivity.java\`** incorpora sección \`QA GPS PRIVADO\` en **Estado del sistema**. Muestra solo un mensaje de estado seguro y legible. La vista no llama la RPC ni inicia una publicación adicional, y no exhibe ubicación, identidad de reservas ni secretos. Estado solo del proceso en curso: se reinicia al cerrar o reiniciar la aplicación.

## Garantías y pruebas

- \`tests/android-p01/PrivateGpsDiagnosticTest.java\`: pruebas Java 17 de enum, códigos conocidos y desconocidos, ausencia de eco del motivo SQL malicioso, prioridad al consentimiento desactivado, transporte, saltos y reinicio lógico de un error. Compila **solo** la clase pura de diagnóstico y el test, sin Gradle ni SDK de Android.
- \`tests/private-gps-p01-static-qa.cjs\`: compara la estructura de los motores y contratos, verifica que se mantiene exactamente una invocación de cada RPC pública y privada, guardas de consentimiento/jornada/precisión, ausencia de nuevos logs de datos, integración de lectura en pantalla y no persistencia.
- CI aislado \`qa-mapqt-p01-private-gps.yml\` no tiene secretos, llamadas a Supabase ni tareas de compilación APK. Artefacto JSON contiene únicamente nombres de las comprobaciones.
- La etiqueta visible \"RPC aceptada\" no equivale a validar que la coordenada esté almacenada/recibida por el pasajero: el SQL puede reportar \`ok:true\` para una muestra antigua cuya actualización se haya omitido.
- Ni la interfaz Android completa ni los motores GPS fueron compilados en esta etapa; la revisión física requiere autorización separada de APK QA firmada y pruebas Samsung.

## Prohibiciones respetadas

No se modificó \`main\`, Supabase real, migraciones SQL, PWA/F1, Conductor, producción, la APK instalada ni reservas de usuario. No se desplegó ni compiló APK. Todo se puede revertir volviendo al SHA Android base.

## Próximo paso condicionado a aprobación

Antes de resolver P0.2 de asignación unívoca reserva ↔ dispositivo o desplegar una compilación QA, corroborar las respuestas con una APK aislada y documentar una matriz de casos, sin borrar/cerrar reservas para forzar éxito.
