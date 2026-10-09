# Traslados Android — índice del repositorio

Este repositorio contiene **más de una aplicación Android**. Antes de programar, elegí el módulo correcto.

## Abrir la web para los pasajeros

**[ABRIR TRASLADOS CLIENTE — Reservar y probar la web](web-pasajero/)**

Acceso directo público: **https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/**

En esta página se realizan las solicitudes de viaje, se consultan reservas y se instala la PWA en Android. **Esta portada es el índice del proyecto, no el formulario de reserva.**

## Mapa Trayectos — empezar aquí
- **[RETOMAR MAPA TRAYECTOS (contexto maestro actualizado)](docs/RETOMAR_MAPA_TRAYECTOS.md)**. Documento de referencia para continuar desde una nueva sesión de ChatGPT.
- [Historial de versiones](docs/HISTORIAL_VERSIONES_MAPA_TRAYECTOS.md).
- [Contexto histórico ampliado](docs/CONTEXTO_MAESTRO_MAPA_TRAYECTOS.md).
- [Roadmap histórico](docs/mapa-trayectos-roadmap-2026-10-08.md).
- Aplicación Android nativa en `mapatrayectos/`. Paquete `uy.com.mapatrayectos`.
- Build firmado: [Mapa Trayectos GitHub Actions](.github/workflows/build-mapa-trayectos.yml).

**Corte documentado:** `0.1-R24.3`, `versionCode 45`, [build firmado exitoso 37924921923](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/37924921923). Este número no sustituye verificar `main` ni las compilaciones posteriores.

**R24.3 backup ZIP (Drive verificado, contiene APK):** https://drive.google.com/file/d/1aoPV7gQIAebhz57ZdpAdBcj9bqYmh-VP/view

**Importante:** R24.3 conserva R24.2 y corrige la ventana de presupuestos de Conductor integrado para desplazar campos por encima del teclado numérico: ajuste dinámico de altura según IME y scroll automático al foco. CI y firma APK OK; falta QA real en Samsung. **No hay push FCM auténticos, ni GPS live en Cliente, ni unificación de GPS.** No borrar datos.

## Otros módulos
- `conductor/` conserva la APK Conductor independiente como respaldo. Sus fuentes también se empaquetan como módulo **interno** en Mapa Trayectos R24.0; no confundir la APK independiente con el nuevo acceso integrado.

## Instrucción mínima para otra sesión
> Retomá Mapa Trayectos en `marcelofgx-ctrl/traslados-android`, rama `main`. Primero leé `docs/RETOMAR_MAPA_TRAYECTOS.md`, verificá el último commit y los GitHub Actions. No cambies el diseño aprobado, los sonidos de viajes ni datos existentes sin consultarme.
