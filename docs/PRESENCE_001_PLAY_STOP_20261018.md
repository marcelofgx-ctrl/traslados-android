# PRESENCE-001 — Play/Stop único de operación comercial (18/10/2026)

## Cambio pedido por el conductor
No volver a ingresar PIN por jornada. El **mismo teléfono previamente vinculado** tiene una identidad cifrada mediante Android Keystore. El servidor sigue exigiendo credenciales verificables, pero Mapa las usa internamente.

**Play (iniciar jornada):**
- Arranca la jornada local existente.
- Solicita en segundo plano habilitar recogidas con la identidad **ya vinculada**, sin pedir PIN ni mostrar otro diálogo de consentimiento en el uso diario.
- Borra el modo «ocupado manualmente» que pudiera haber quedado de una sesión anterior.
- Con red caída, muestra diagnóstico y reintenta de manera limitada (cada ~60 segundos), solamente mientras la jornada y el pedido de Play sigan activos.
- La publicación real sigue dependiendo de GPS apto, horario y disponibilidad del servidor. No inventar disponibilidad ni ETA.

**Stop (finalizar jornada):**
- Conserva el bloqueo existente de Stop si hay un viaje activo y la confirmación de cierre para no perder viajes.
- Desactiva **inmediatamente** la publicación comercial local, incluso si no hay internet.
- Encola la revocación remota detrás de operaciones Play pendientes; al completar el servidor borra las coordenadas comerciales. Si la red falla, el servidor caduca la posición según su TTL vigente.
- Mantiene GPS histórico, jornadas y reservas. No corta notificaciones privadas de reservas ya vinculadas.

**Identidad inicial y privacidad:** un teléfono nuevo o desvinculado debe identificarse una sola vez; NO eliminar la autenticación del servidor. El control Play en un teléfono no vinculado puede iniciar el registro local de jornada, **pero nunca publicar coordenadas comerciales**, y el diagnóstico lo indica. Una desactivación manual de recogidas también deshabilita el reintento automático hasta otro Play intencional.

## Implementación y QA
- `TrackingService.startShift / stopShift / clockTick` → `Api.commercialPlay / commercialStop / retryCommercialPlay`.
- Cola serial para que un Play lento no sea el estado final después de Stop.
- Contrato Java puro `PlayStopPresencePolicyTest` + comprobaciones de cableado en workflow modular, sin nueva API ni migración de Supabase.
- Esta propuesta **sustituye la oferta automática de consentimiento al abrir** planteada en PR #137, que no debe integrarse junto con el flujo Play/Stop. Se basa en el diagnóstico de PR #136.
- PR en revisión, no producción. Android Debug/Release y firma/same-app/versionCode/QA Samsung pendientes. No aprobar por pruebas estáticas exclusivamente.

## APK de revisión

R25.34-PLAYSTOP-QA / versionCode 86, mismo package y certificado R25.30+. Se verifica contra firmado anterior, pruebas de datos/GPS/historial, tests de Play/Stop, Android Build. Solo artefacto temporal de Actions. No release pública.
