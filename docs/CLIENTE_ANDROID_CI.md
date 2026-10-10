# Traslados Cliente — APK nativa, publicación y CI

**Corte:** 09/10/2026 (Uruguay). **Documento técnico exclusivo de Cliente Android**. Coordinación general: [CONTEXTO_MAESTRO_PROYECTOS.md](CONTEXTO_MAESTRO_PROYECTOS.md).

## Estado comprobado

- Entregable **Cliente Android nativo 11.5-R10 RELEASE**, build y firma verificados en [GitHub Actions 37945735891](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/37945735891), artefacto `Traslados-Cliente-v11.5-R10-RELEASE` ID **11624055221**. Incluye ZIP con APK firmada y fuente reconstruida.
- APK respaldada en Drive (enlace ya documentado): https://drive.google.com/file/d/1zmINKO6Xcjc6muVpRgwBHZ5uC2s2NFUV/view . SHA-256 APK informada en [ESTADO_INTEGRADO_CLIENTE_WEB_PWA_CONDUCTOR.md](ESTADO_INTEGRADO_CLIENTE_WEB_PWA_CONDUCTOR.md): `59c24c33c83f9a19648ca1cf280d64b5167995751cc663965d2b4a1fb1d5e5f4`. **No confundir hash de APK con el del ZIP de Actions.**
- Workflow RELEASE actual: `.github/workflows/build-cliente-v11-5-R10.yml`. Produce `cliente-release.apk`; recupera base Android histórica y un snapshot fuente de R9.1 antes de aplicar `cliente-pasajero/scripts/build_cliente_r10.py`. Por tanto, **aún no es 100 % autónomo desde archivos fuente sin dependencias históricas**; esto es deuda técnica de reproducibilidad, no un fallo demostrado de R10.
- **Prueba Samsung y ciclo completo Supabase pendientes**: las ejecuciones de Actions prueban compilación/firma pero no reserva real, presupuesto, historial, visual móvil ni que se conserve el PIN en una actualización local.

## Incidente de CI legado: «Build Traslados Cliente v9.0»

Hasta esta revisión el workflow `.github/workflows/build-apks.yml` corría en **cada push a main**, construía una APK **9.0 DEBUG** a partir de archivos tar/gzip históricos embebidos, y el artefacto se llamaba `Traslados-Cliente-v9.0`. Ejemplo: [38008909633](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38008909633) **SUCCESS**, artefacto `Traslados-Cliente-v9.0` ID **11653040621**, ZIP de ~75 KB. **No es una actualización de 11.5-R10**, ni release firmada, ni sustituye el artefacto válido.

Para evitar que cada modificación documental publicara una compilación «nueva» engañosa y para separar las versiones, el YAML legado se **retiró de main** en [3fe7ad0](https://github.com/marcelofgx-ctrl/traslados-android/commit/3fe7ad0396d0f3e6ac41bad49b72e26c2c2b1cc3). Los commits y jobs históricos permanecen en Git. Un último job que hubiera sido encolado **antes** de borrarlo no implica una nueva APK release.

## No confundir los tres clientes

1. **Cliente Android:** APK nativa (este documento), release 11.5-R10 probada por CI; instalación y funcionalidad por confirmar en Samsung.
2. **Pasajero GitHub Pages:** `/web-pasajero/` PWA instalable desde navegador, **no APK**. Su workflow de publicación es distinto.
3. **Web Premium Cloudflare:** repositorio `traslados-web`, versión/frontend separados de GitHub Pages aunque comparten Supabase `zetaudvvutlouiqxopvg`.

## Próximas prioridades

- Registrar evidencia de una **única reserva real consentida** Cliente Android → Conductor integrado → presupuesto → aceptación/rechazo → historial; contrastar identificadores, RLS y tiempos sin duplicar reservas.
- Quitar gradualmente la dependencia del workflow RELEASE de snapshots/paquetes externos históricos, guardando el código completo de Cliente en `cliente/` o `cliente-pasajero/` con pruebas reproducibles.
- No actualizar apps mediante desinstalación ni mezclar APK debug y release con firmas distintas sin respaldo de datos. La ausencia de push FCM y GPS en vivo no equivale a «listo».
- Tras próximas compilaciones, actualizar este documento y reflejar su estado resumido en el contexto maestro.
