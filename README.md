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
- [Mapa QT — estado automático y enlace de la última APK verificada](docs/ESTADO_AUTOMATICO_TRASLADOS.md) · verificar firma y versión antes de instalar
- **No instalar APK Cliente ni APK Conductor independientes:** son archivos históricos y no corresponden a la arquitectura vigente
- [Backend — traslados-web](https://github.com/marcelofgx-ctrl/traslados-web)

Los enlaces vigentes muestran la web única y el estado técnico verificado. Las descargas antiguas permanecen archivadas en Git para posibles rollbacks, pero dejan de promocionarse como aplicaciones actuales.

## Organización

- `web-pasajero/` · única web/PWA de pasajeros; no se distribuye una APK Cliente independiente.
- `conductor/` · módulo Android histórico/conductor integrado en Mapa; no se empaqueta como APK independiente.
- `mapatrayectos/` · única APK Android Mapa QT con GPS y módulo Conductor integrado.
- `core-reservations/` · código compartido Android.
- `docs/` · contexto, decisiones, QA e historia.
- `scripts/estado_traslados.py` + [workflow automático](.github/workflows/estado-tecnico-auto.yml) · regeneran únicamente estado técnico, sin builds.

## Criterios de continuidad

Primero consultar versiones reales y últimos workflows; después decisiones y pendientes. No confundir CI exitoso con migración Supabase aplicada, Cloudflare publicado o pruebas físicas Samsung. No repetir explicaciones históricas ni solicitar al usuario información ya documentada.

**Una sola actualización por fase:** la automatización registra versiones y builds; el documento funcional solo se actualiza si cambian decisiones o el estado de QA. No hay obligación de reescribir todo el contexto.

---

[README histórico anterior (cortes y referencias antiguas)](docs/README_HISTORICO_20261013.md).
