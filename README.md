# Traslados Android — índice del repositorio

Este repositorio contiene **más de una aplicación Android**. Antes de programar, elegí el módulo correcto.

## Mapa Trayectos — empezar aquí
- **[RETOMAR MAPA TRAYECTOS (contexto maestro actualizado)](docs/RETOMAR_MAPA_TRAYECTOS.md)**. Documento de referencia para continuar desde una nueva sesión de ChatGPT.
- [Historial de versiones](docs/HISTORIAL_VERSIONES_MAPA_TRAYECTOS.md).
- [Contexto histórico ampliado](docs/CONTEXTO_MAESTRO_MAPA_TRAYECTOS.md).
- [Roadmap histórico](docs/mapa-trayectos-roadmap-2026-10-08.md).
- Aplicación Android nativa en `mapatrayectos/`. Paquete `uy.com.mapatrayectos`.
- Build firmado: [Mapa Trayectos GitHub Actions](.github/workflows/build-mapa-trayectos.yml).

**Corte documentado:** `0.1-R23.1`, `versionCode 41`, [build firmado exitoso 37881774310](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/37881774310). Este número no sustituye verificar `main` ni las compilaciones posteriores.

**Enlace de APK R23.1 (Drive verificado):** https://drive.google.com/file/d/1WLYfqWfyt-VDvdij_WSUc1MpR_0xTrsm/view

**Importante:** R23.1 muestra la burbuja al minimizar aun sin jornada, SIN activar GPS, mantiene la flecha anclada de R23.0 y el MP3 de ElevenLabs. Build y pruebas automatizadas OK; faltan pruebas reales en Samsung. No borrar datos. No borrar bases locales ni tocar Supabase sin autorización.

## Otros módulos
- `conductor/` es otra aplicación. **No confundir Traslados Conductor con Mapa Trayectos**.

## Instrucción mínima para otra sesión
> Retomá Mapa Trayectos en `marcelofgx-ctrl/traslados-android`, rama `main`. Primero leé `docs/RETOMAR_MAPA_TRAYECTOS.md`, verificá el último commit y los GitHub Actions. No cambies el diseño aprobado, los sonidos de viajes ni datos existentes sin consultarme.
