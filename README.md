# Traslados Android — índice del repositorio

Este repositorio contiene **más de una aplicación Android**. Antes de programar, elegí el módulo correcto.

## Mapa Trayectos — empezar aquí
- **[RETOMAR MAPA TRAYECTOS (contexto maestro actualizado)](docs/RETOMAR_MAPA_TRAYECTOS.md)**. Documento de referencia para continuar desde una nueva sesión de ChatGPT.
- [Historial de versiones](docs/HISTORIAL_VERSIONES_MAPA_TRAYECTOS.md).
- [Contexto histórico ampliado](docs/CONTEXTO_MAESTRO_MAPA_TRAYECTOS.md).
- [Roadmap histórico](docs/mapa-trayectos-roadmap-2026-10-08.md).
- Aplicación Android nativa en `mapatrayectos/`. Paquete `uy.com.mapatrayectos`.
- Build firmado: [Mapa Trayectos GitHub Actions](.github/workflows/build-mapa-trayectos.yml).

**Corte documentado:** `0.1-R24.1`, `versionCode 43`, [build firmado exitoso 37918720480](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/37918720480). Este número no sustituye verificar `main` ni las compilaciones posteriores.

**APK R24.1 (Drive verificado):** https://drive.google.com/file/d/1spEV5CniaJIm16xCIAjH6M8oDctae53w/view

**Importante:** R24.1 conserva la integración Conductor R24.0, reduce menú ☰ y corrige badge del globito, permite cerrar mapa con pulsación larga solamente sin jornada abierta, preservando monitor de reservas mientras Android lo permita. **No hay push FCM remotos todavía**, solo monitor de Supabase por polling. CI y firma OK; faltan QA real Samsung, consolidación GPS y ubicación en vivo del cliente. No borrar datos.

## Otros módulos
- `conductor/` conserva la APK Conductor independiente como respaldo. Sus fuentes también se empaquetan como módulo **interno** en Mapa Trayectos R24.0; no confundir la APK independiente con el nuevo acceso integrado.

## Instrucción mínima para otra sesión
> Retomá Mapa Trayectos en `marcelofgx-ctrl/traslados-android`, rama `main`. Primero leé `docs/RETOMAR_MAPA_TRAYECTOS.md`, verificá el último commit y los GitHub Actions. No cambies el diseño aprobado, los sonidos de viajes ni datos existentes sin consultarme.
