# Revisión integral de Traslados — primera mejora de producto R25.9

Fecha: 13/10/2026. Estado de este documento: revisión de código y plan de trabajo, **no equivale a QA Samsung físico**.

## Diagnóstico

El ecosistema ya cuenta con pasajero web PWA, app cliente, conductor integrado y Mapa Trayectos, jornadas, indicadores GPS, recordatorios, burbuja y monitor de traslados. La principal deuda ahora es de **coherencia, simplicidad y confiabilidad del recorrido de usuario**, más que agregar módulos aislados.

1. **Enlaces de captación incorrectos (riesgo comercial):** `QuickActionsMenu` aún ofrecía el Worker de Cloudflare — backend técnico — como web de reservas y el dominio viejo de Lovable como descarga. La referencia pública aprobada es GitHub Pages `/web-pasajero/`; la APK oficial de pasajero v15 está publicada en `/web-pasajero/downloads/traslados-cliente-premium-v15.apk`. Los enlaces conocidos obsoletos deben migrar en teléfonos con preferencia guardada, sin sobreescribir URLs personalizadas.
2. **Permisos sin jerarquía:** seis acciones indistintas en Configuración; al usuario le importa habilitar, comprobar y saber si está listo. Las rutas técnicas y diagnósticos deben existir, pero no competir visualmente con la acción principal.
3. **Notificaciones sin solución directa:** el panel informaba desactivadas pero no dirigía a los ajustes de notificaciones de *esta misma app*. Solución: botón contextual `ACTION_APP_NOTIFICATION_SETTINGS + EXTRA_APP_PACKAGE` con retorno a panel actualizado.
4. **Ajustes difíciles de leer y tocar:** textos de 11,5sp, botones iguales y accesos pequeños. Diseñar superficie petróleo/champagne con contraste, estados inequívocos, acciones 62dp y menú principal adaptativo de ~282dp, filas 54dp, títulos 12.8sp y descripciones 10.4sp.
5. **Soporte sin evidencia fácil:** agregar botón **Copiar informe** que entregue permisos, notificaciones, último estado reportado y visibilidad registrada, sin coordenadas, PIN, datos de clientes ni ubicaciones. Aclarar que el globito se oculta con Mapa en primer plano; última visibilidad no equivale a chequeo en vivo.

## Implementación R25.9 — primer lote pequeño, real y reversible

- Centro de control premium reconstruido en `BubbleSettingsPanel.java`: estado visual acceso flotante/notificaciones, CTA principal para permiso, prueba de globito, activación de notificaciones solo si están deshabilitadas, último evento, refresco, plegado "Más opciones" con rutas alternativas, diagnóstico y copiar informe local.
- `MainActivity.java`: acceso específico a ajustes de notificaciones de Mapa Trayectos, volver y refrescar panel; no se toca la política de permisos del globito ni TrackingService, ni PIN ni seguimiento GPS; menú principal con filas mayores, lectura y ancho adaptativo.
- `QuickActionsMenu.java`: links cliente/booking de producción; migrar solo defaults conocidos antiguos (Worker y Lovable), mantener personalizaciones, confirmar destino en primer envío tras migración.
- `mapatrayectos/build.gradle`: R25.9 versionCode 61. Publicar APK firmada por el workflow regular; comprobar CI, firma, descarga, checksum antes de declarar entrega.
- Alcance excluido deliberadamente: no cambios de datos o reglas Supabase, cotizaciones, reservas ni desmontaje de jornadas/Conductor; no añadir permisos de alto riesgo.

## QA de aceptación — Samsung

1. Instalar R25.9 **encima de la APK previa, sin desinstalar**. Confirmar inicio, GPS y recorridos existentes; no borrar preferencias ni base.
2. `☰ → Configuración`: comprobar status de ambos permisos, botones legibles con fuente Samsung ampliada, sin desbordes; "Más opciones" se abre/cierra, conserva accesos Android específicos.
3. Si notificaciones están desactivadas, "Activar notificaciones" debe abrir ajustes de **Mapa Trayectos**, no lista de apps. Volver y comprobar el nuevo estado. Si están activas, evitar botón redundante.
4. "Probar globito" abre Inicio y aparece el globito si el sistema lo autoriza; con Mapa en foreground sigue intencionalmente oculto. Comprobar diagnóstico.
5. "Copiar informe" pega texto sin PIN, origen/destino, ubicación ni datos del cliente. No depender de red.
6. `✦ → Enlaces y contacto`: Web reservas a Pages PWA, descarga a APK Cliente Premium v15. Si usuario tenía enlace propio confirmado, respetarlo. Si conservaba Worker/Lovable antiguo, cambiarlo y pedir confirmación; probar copia/compartir.
7. Revisar menú principal con letras grandes: secciones legibles, botones seleccionables, sin pérdida de accesos a Conductor, historial, recordatorios, mantenimiento ni disponibilidad.

## Hoja de ruta sugerida

- **Siguiente ciclo (impacto alto, requiere QA real):** diagrama y prueba completa solicitud inmediata/programada → envío de presupuesto → aceptación/rechazo → Conductor → traslado finalizado, con una sola línea de estado coherente en todas las superficies, notificación fiable y manejo de expiración. Evitar duplicar botones o mensajes; no confirmar automáticamente una reserva.
- **Siguiente ciclo técnico (impacto alto):** persistir km/min estimados por carretera de solicitudes cuando proveedor de rutas responda, con marca de precisión y alternativa explícita cuando falte información; no confundir estimación con GPS real y no inventar ETA.
- **Siguiente ciclo visual:** tablero de conductor plegable por hoy/próximos días/historial; chips discretos Pendiente/Cotizado/Confirmado/En camino/Finalizado; acciones contextuales por estado, prioridad a una tarea a la vez, vibración/sonido/push verificadas en Samsung.
- **Siguiente ciclo Mapa:** simplificar capa de botones y distribuir información por prioridad conducción/pausa/servicio, revisar contraste y geometría táctil sobre mapa; evitar obstaculizar navegación vial.
- **Ejecución:** cambios por lotes verificables con QA, sin tocar lógica productiva crítica por estética. Mantener estado en `docs/REANUDAR_TRASLADOS.md` y `docs/ESTADO_AUTOMATICO_TRASLADOS.md`.
