# Traslados / APK Mapa — repositorio principal

> **TRASPASO DEL 18/10/2026:** [Metodología aplicada, PR pendientes y diagnóstico de las 12 capturas](docs/CONTINUIDAD_METODOLOGIA_Y_CAPTURAS_20261018.md). Es el contexto reciente para retomar la sesión; las imágenes originales quedaron en paquete privado del usuario, no en GitHub.

> **NORMA PERMANENTE DESDE EL 18/10/2026:** antes de comenzar otra sesión o modificar código, leer [METODOLOGÍA PERMANENTE](docs/METODOLOGIA_PERMANENTE_TRASLADOS.md) y [REGISTRO DE REGRESIONES](docs/REGISTRO_DE_REGRESIONES_TRASLADOS.md). Ninguna compilación/PR equivale automáticamente a publicación o QA física. Producto oficial: página pública, web/PWA pasajeros y **única APK Mapa Trayectos con Conductor integrado**. Los enlaces antiguos a APK Cliente y Conductor en esta página son **históricos**, NO productos oficiales nuevos.\n\n
**Entrada única para retomar el proyecto en cualquier sesión:**

1. [ESTADO AUTOMÁTICO — versiones, APK y GitHub Actions](docs/ESTADO_AUTOMATICO_TRASLADOS.md) · generado sin recompilar ni publicar nada.
2. [REANUDAR TRASLADOS — decisiones, arquitectura y pendientes](docs/REANUDAR_TRASLADOS.md) · se edita únicamente cuando cambian decisiones o QA.
3. [Contexto histórico completo](docs/CONTEXTO_MAESTRO_PROYECTOS.md) · consultar solo para profundizar.

**Frase única para otra sesión:** «**Retomar Traslados Web desde Git**». Esta frase designa todo el ecosistema (PWA, APK, conductor, Mapa, backend) y conduce a los dos documentos enlazados arriba.

**Al retomar:** informar primero **fecha y hora en Uruguay de la última compilación verificada y el último cambio de código**, y sus enlaces; después el pendiente actual. **Al finalizar cualquier compilación informada al usuario**, devolver el resumen de la compilación y repetir al final la misma frase exacta para copiar y pegar.

## Accesos

- [Web única del pasajero](https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/)
- [Descarga Cliente Premium](https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/downloads/traslados-cliente-premium-v15.apk)
- [Descarga Conductor](https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/downloads/traslados-conductor-r6.apk)
- [Descarga Mapa Trayectos](https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/downloads/mapa-trayectos-r25.0.apk)
- [Backend — traslados-web](https://github.com/marcelofgx-ctrl/traslados-web)

Los enlaces anteriores corresponden al corte del 13/10/2026; **verificar el estado automático antes de anunciar que son la última versión**.

## Organización

- `web-pasajero/` · PWA publicada por GitHub Pages; Cliente Premium usa la misma interfaz vía WebView.
- `conductor/` · APK nativa Conductor, presupuestos y reservas.
- `mapatrayectos/` · APK nativa Mapa Trayectos y GPS.
- `core-reservations/` · código compartido Android.
- `docs/` · contexto, decisiones, QA e historia.
- `scripts/estado_traslados.py` + [workflow automático](.github/workflows/estado-tecnico-auto.yml) · regeneran únicamente estado técnico, sin builds.

## Criterios de continuidad

Primero consultar versiones reales y últimos workflows; después decisiones y pendientes. No confundir CI exitoso con migración Supabase aplicada, Cloudflare publicado o pruebas físicas Samsung. No repetir explicaciones históricas ni solicitar al usuario información ya documentada.

**Una sola actualización por fase:** la automatización registra versiones y builds; el documento funcional solo se actualiza si cambian decisiones o el estado de QA. No hay obligación de reescribir todo el contexto.

---

[README histórico anterior (cortes y referencias antiguas)](docs/README_HISTORICO_20261013.md).
