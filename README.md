# Traslados Android — índice del repositorio

Este repositorio contiene **más de una aplicación Android**. Antes de programar, elegí el módulo correcto.

## Mapa Trayectos — empezar aquí
- **[RETOMAR MAPA TRAYECTOS (contexto maestro actualizado)](docs/RETOMAR_MAPA_TRAYECTOS.md)**. Documento de referencia para continuar desde una nueva sesión de ChatGPT.
- [Historial de versiones](docs/HISTORIAL_VERSIONES_MAPA_TRAYECTOS.md).
- [Contexto histórico ampliado](docs/CONTEXTO_MAESTRO_MAPA_TRAYECTOS.md).
- [Roadmap histórico](docs/mapa-trayectos-roadmap-2026-10-08.md).
- Aplicación Android nativa en `mapatrayectos/`. Paquete `uy.com.mapatrayectos`.
- Build firmado: [Mapa Trayectos GitHub Actions](.github/workflows/build-mapa-trayectos.yml).

**Corte documentado:** `0.1-R24.2`, `versionCode 44`, [build firmado exitoso 37922498198](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/37922498198). Este número no sustituye verificar `main` ni las compilaciones posteriores.

**APK R24.2 (Drive verificado):** https://drive.google.com/file/d/1XwaSF9jkxDJKmjhNh5ltD9B8g-IXf1le/view

**Importante:** R24.2 integra Centro rápido plegable en ✦ con reservas auténticas de Supabase, próxima salida, agenda agrupada, detalles, navegación Waze/Maps, WhatsApp y alertas de datos obsoletos y posibles superposiciones; conserva R24.1 y Conductor interno. CI/firma APK OK; aún falta QA real Samsung. **No hay push FCM auténticos, ni GPS live en app Cliente, ni unificación de servicios GPS**. No borrar datos.

## Otros módulos
- `conductor/` conserva la APK Conductor independiente como respaldo. Sus fuentes también se empaquetan como módulo **interno** en Mapa Trayectos R24.0; no confundir la APK independiente con el nuevo acceso integrado.

## Instrucción mínima para otra sesión
> Retomá Mapa Trayectos en `marcelofgx-ctrl/traslados-android`, rama `main`. Primero leé `docs/RETOMAR_MAPA_TRAYECTOS.md`, verificá el último commit y los GitHub Actions. No cambies el diseño aprobado, los sonidos de viajes ni datos existentes sin consultarme.
