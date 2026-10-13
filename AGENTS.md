# AGENTS.md — Traslados / APK Mapa

> Instrucciones de continuidad para asistentes y agentes que abran este repositorio. **Primero contexto, después código.**

1. Leer [estado técnico automático](docs/ESTADO_AUTOMATICO_TRASLADOS.md) para versión y builds. Contrastar con `main` y últimos GitHub Actions: no asumir que la foto sigue actualizada.
2. Leer [contexto de reanudación](docs/REANUDAR_TRASLADOS.md) para arquitectura, decisiones, comportamiento aprobado, backlog y QA. Consultar documentos históricos solo si hace falta un detalle.
3. La interfaz pública aprobada del pasajero es [GitHub Pages](https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/). **No** reemplazarla por Cloudflare Worker, que presta APIs auxiliares.
4. Cada cambio debe ser acotado, preservar estilo/UX, autenticación, GPS, presupuestos, horarios y compatibilidad Android. No fusionar ni desplegar cambios sin pruebas del módulo pertinente.
5. Backend: migraciones versionadas en `marcelofgx-ctrl/traslados-web`; **CI verde no equivale a migración aplicada ni Worker desplegado**. Comprobar antes de afirmar.
6. Android: mantener firma/certificado, incrementar `versionCode` al cambiar APK nativa y verificar release + archivo publicado. La PWA usa WebView Cliente Premium; cambios web no requieren recompilar la APK si no cambia código nativo.
7. Evitar tocar reservas reales, clientes, PIN, tokens, claves, ubicación precisa o notificaciones sin alcance explícito. Tests sintéticos con rollback y resultados comprobables.
8. Estado de versiones, enlaces y GitHub CI: lo actualiza **automáticamente** `.github/workflows/estado-tecnico-auto.yml` mediante `scripts/estado_traslados.py`. **No reescribir manualmente números de versión** en el contexto en cada commit.
9. Registrar en [contexto de reanudación](docs/REANUDAR_TRASLADOS.md) **solo decisiones funcionales, cambios de alcance, hallazgos QA y pendientes** al cerrar una fase. No regenerar grandes historiales para cambiar de sesión.
10. Usuario puede retomar con «Retomá Traslados desde GitHub: marcelofgx-ctrl/traslados-android»; abrir las dos referencias anteriores antes de pedir información repetida.

Los documentos tienen prioridad contextual, pero el código y los resultados reales publicados tienen prioridad factual si discrepan de documentación antigua.
