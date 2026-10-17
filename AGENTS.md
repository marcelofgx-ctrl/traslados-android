# AGENTS.md — Traslados / APK Mapa

> Instrucciones de continuidad para asistentes y agentes que abran este repositorio. **Primero contexto, después código.**

1. Leer [estado técnico automático](docs/ESTADO_AUTOMATICO_TRASLADOS.md) para versión y builds. Contrastar con `main` y últimos GitHub Actions: no asumir que la foto sigue actualizada.
2. Leer [contexto de reanudación](docs/REANUDAR_TRASLADOS.md) para arquitectura, decisiones, comportamiento aprobado, backlog y QA. Consultar documentos históricos solo si hace falta un detalle.
3. La interfaz pública aprobada del pasajero es [GitHub Pages](https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/). **No** reemplazarla por Cloudflare Worker, que presta APIs auxiliares.
4. Cada cambio debe ser acotado, preservar estilo/UX, autenticación, GPS, presupuestos, horarios y compatibilidad Android. No fusionar ni desplegar cambios sin pruebas del módulo pertinente.
5. Backend: migraciones versionadas en `marcelofgx-ctrl/traslados-web`; **CI verde no equivale a migración aplicada ni Worker desplegado**. Comprobar antes de afirmar.
6. **Única APK Android de producto: Mapa Trayectos.** Mantener su firma/certificado y aumentar `versionCode` cuando cambie Mapa; verificar release y archivo publicado. Funciones de Conductor están integradas dentro de Mapa como fuentes internas, **no** compilar ni publicar APK independiente Conductor. Pasajeros usan exclusivamente web/PWA; **no** compilar APK Cliente/WebView. Los cambios web no requieren APK.
7. Evitar tocar reservas reales, clientes, PIN, tokens, claves, ubicación precisa o notificaciones sin alcance explícito. Tests sintéticos con rollback y resultados comprobables.
8. Estado de versiones, enlaces y GitHub CI: lo actualiza **automáticamente** `.github/workflows/estado-tecnico-auto.yml` mediante `scripts/estado_traslados.py`. **No reescribir manualmente números de versión** en el contexto en cada commit.
9. Registrar en [contexto de reanudación](docs/REANUDAR_TRASLADOS.md) **solo decisiones funcionales, cambios de alcance, hallazgos QA y pendientes** al cerrar una fase. No regenerar grandes historiales para cambiar de sesión.
10. **Disparador corto en cualquier sesión:** `Retomar Traslados Web desde Git` (admitir variantes de mayúsculas, singular/plural). Significa el ecosistema completo, **no solo el Worker/backend**. Usar el repositorio GitHub `marcelofgx-ctrl/traslados-android`, rama `main`; consultar `docs/ESTADO_AUTOMATICO_TRASLADOS.md` y `docs/REANUDAR_TRASLADOS.md`, contrastando con GitHub actual.
11. **Obligatorio al iniciar una sesión por ese disparador:** antes de proponer cambios, informar **fecha y hora (America/Montevideo) de la última compilación verificada** (por aplicación o global identificada), y **fecha y hora del último cambio funcional** en el ecosistema, con resultado, componente y enlaces GitHub. Si no hay dato fiable, señalar «no verificable», sin inventarlo ni confundir auto-commits de documentación con código.
12. **Obligatorio al cerrar una compilación o publicación informada en ChatGPT**, exitosa o fallida: indicar estado, versión, fecha/hora local y enlace al run cuando existan. Terminar con una frase fácilmente copiable, exacta: **Retomar Traslados Web desde Git**. Esto es un protocolo de respuesta, no un aviso proactivo garantizado ni una tarea en segundo plano.

Los documentos tienen prioridad contextual, pero el código y los resultados reales publicados tienen prioridad factual si discrepan de documentación antigua.
