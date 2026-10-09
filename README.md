# Traslados Android — índice del repositorio

Este repositorio contiene **más de una aplicación Android**. Antes de programar, elegí el módulo correcto.

## Mapa Trayectos — empezar aquí
- **[RETOMAR MAPA TRAYECTOS (contexto maestro actualizado)](docs/RETOMAR_MAPA_TRAYECTOS.md)**. Documento de referencia para continuar desde una nueva sesión de ChatGPT.
- [Historial de versiones](docs/HISTORIAL_VERSIONES_MAPA_TRAYECTOS.md).
- [Contexto histórico ampliado](docs/CONTEXTO_MAESTRO_MAPA_TRAYECTOS.md).
- [Roadmap histórico](docs/mapa-trayectos-roadmap-2026-10-08.md).
- Aplicación Android nativa en `mapatrayectos/`. Paquete `uy.com.mapatrayectos`.
- Build firmado: [Mapa Trayectos GitHub Actions](.github/workflows/build-mapa-trayectos.yml).

**Corte documentado:** `0.1-R23.0`, `versionCode 40`, [build firmado exitoso 37880538141](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/37880538141). Este número no sustituye verificar `main` ni las compilaciones posteriores.

**Enlace de la APK R23.0 (Drive verificado):** https://drive.google.com/file/d/1HFX76Ldit7hKNRMrTbcLyE_8WgmI2V0C/view

**Importante:** R23.0 incorpora el arreglo de anclaje geométrico del globo de recordatorio a ✦ o la burbuja flotante, mantiene el MP3 original ElevenLabs y el diseño premium. Build y pruebas automatizadas OK; faltan pruebas visuales y auditivas reales en Samsung. No borrar bases locales ni tocar Supabase sin autorización. No borrar bases locales ni tocar Supabase sin autorización.

## Otros módulos
- `conductor/` es otra aplicación. **No confundir Traslados Conductor con Mapa Trayectos**.

## Instrucción mínima para otra sesión
> Retomá Mapa Trayectos en `marcelofgx-ctrl/traslados-android`, rama `main`. Primero leé `docs/RETOMAR_MAPA_TRAYECTOS.md`, verificá el último commit y los GitHub Actions. No cambies el diseño aprobado, los sonidos de viajes ni datos existentes sin consultarme.
