# Traslados Android — índice del repositorio

Este repositorio contiene **más de una aplicación Android**. Antes de programar, elegí el módulo correcto.

## Mapa Trayectos — empezar aquí
- **[RETOMAR MAPA TRAYECTOS (contexto maestro actualizado)](docs/RETOMAR_MAPA_TRAYECTOS.md)**. Documento de referencia para continuar desde una nueva sesión de ChatGPT.
- [Historial de versiones](docs/HISTORIAL_VERSIONES_MAPA_TRAYECTOS.md).
- [Contexto histórico ampliado](docs/CONTEXTO_MAESTRO_MAPA_TRAYECTOS.md).
- [Roadmap histórico](docs/mapa-trayectos-roadmap-2026-10-08.md).
- Aplicación Android nativa en `mapatrayectos/`. Paquete `uy.com.mapatrayectos`.
- Build firmado: [Mapa Trayectos GitHub Actions](.github/workflows/build-mapa-trayectos.yml).

**Corte documentado:** `0.1-R24.0`, `versionCode 42`, [build firmado exitoso 37887653127](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/37887653127). Este número no sustituye verificar `main` ni las compilaciones posteriores.

**Respaldo Drive R24.0 (ZIP contiene APK firmada):** https://drive.google.com/file/d/17TlWjNWmt-KMp_eX_cNy1tIjG_neKj2C/view

**Importante:** R24.0 integra código nativo completo de Traslados Conductor en Mapa Trayectos (desde carpeta fuente `conductor/` al generar APK), nuevo menú ☰ y avisos pedidos en globito ✦. Mapa y Cliente conservan sus funciones propias. CI y firma OK; pruebas reales Samsung, consolidación de motores GPS y ubicación en vivo del cliente pendientes. No borrar datos ni tocar Supabase sin autorización.

## Otros módulos
- `conductor/` conserva la APK Conductor independiente como respaldo. Sus fuentes también se empaquetan como módulo **interno** en Mapa Trayectos R24.0; no confundir la APK independiente con el nuevo acceso integrado.

## Instrucción mínima para otra sesión
> Retomá Mapa Trayectos en `marcelofgx-ctrl/traslados-android`, rama `main`. Primero leé `docs/RETOMAR_MAPA_TRAYECTOS.md`, verificá el último commit y los GitHub Actions. No cambies el diseño aprobado, los sonidos de viajes ni datos existentes sin consultarme.
