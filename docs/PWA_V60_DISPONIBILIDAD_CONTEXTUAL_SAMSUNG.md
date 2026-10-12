# PWA v60 · Disponibilidad y llegada claras (rama de revisión)

**12/10/2026.** Partimos de producción **PWA v59**, Mapa **R25.0**, Conductor **R5** y Cliente Premium WebView **v15**. La nueva PWA **v60 está solamente en la rama de revisión**, no publicada en main; las APK firmadas anteriores siguen válidas.

## Criterio visual y comunicación
- La tarjeta inmediata responde en este orden: **¿está disponible?** → **¿a cuántos km del origen?** → **¿en cuánto llegaría aproximadamente?**
- Solo se presentan km y ETA **si** el backend ha confirmado disponibilidad y el servicio `pickup-eta` ha ofrecido una estimación válida. Nunca se deriva la posición precisa del conductor ni se confunden los kilómetros A→B con conductor→origen.
- Las frases largas de servidor/agenda/estado cada 15 segundos pasan a un pie discreto **«Actualizado ahora · Se actualiza solo»**. No se promete rastreo instantáneo.
- Sin origen: «Elegí tu origen para ver cuánto tardaría en llegar». Sin disponibilidad: «Recogida inmediata no disponible». Fallo de consulta: «No pudimos comprobar la disponibilidad». Durante cálculo se ve una línea animada; fuera del cálculo no se muestra animación decorativa de un viaje inexistente.
- Una ETA ya calculada se indica como aproximada y puede variar. Si el estado deja de estar disponible, **se eliminan los antiguos km/min**. El pasajero sabe que **la recogida requiere confirmación**.
- El lector de pantalla no debe recibir anuncios nuevos por repintados idénticos cada 15 s. Se respeta `prefers-reduced-motion`.

## Contratos conservados
No cambia Supabase, RPCs de disponibilidad ni ETA, cadencias 15 s/25–45 s, cuotas, privacidad, recorridos, tarifas, chat, presupuestos, jornadas ni GPS. No se modifica `cliente-premium/`, `conductor/` ni `mapatrayectos/`. La APK Cliente Premium v15 seguirá mostrando la PWA por WebView si después se publica.

## Pruebas antes de publicarse
1. CI PWA y revisión: sintaxis, smoke de presencia pública, cuota, fallo de red, sesión anónima/autenticada, disponibilidad ocupada, limpieza de ETA y accesibilidad; sumar `pickup-context-copy-v60-smoke.cjs`.
2. QA **Samsung Chrome y WebView** 360–412 px: sin desplazamiento horizontal, textos completos, sin salto de las tarjetas, teclado y detalles plegables abiertos durante >40 s.
3. Caso **disponible con origen**: km al origen, ETA como rango aproximado, sin indicación de GPS exacto ni disponibilidad garantizada.
4. **Sin origen**: no se muestran km/min viejos. **Ocupado**: sin km/min aunque antes fueran válidos. **Red desconectada**: mostrar reintento, no «conductor ocupado». **Programar**: no mostrar afirmación de disponibilidad inmediata.
5. Chat archivado sin editor; activo con enviar explícito; apertura desde globito Mapa hasta la reserva exacta (solo pruebas de extremo a extremo con reserva autorizada).
6. No reclamar push de Cliente cerrado (FCM pendiente). No crear viajes de prueba ni enviar mensajes reales sin autorización.

**Gate:** mantener la rama/PR en revisión hasta comprobar CI y QA táctil Samsung. Solo entonces fusionar a main/publicar Pages. El registro de producción vigente sigue siendo `RELEASE_TRASLADOS_20261012_PWA59_MAPA25_CONDUCTOR_R5.md`.
