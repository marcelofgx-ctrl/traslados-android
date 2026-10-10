# Traslados Conductor independiente — CI, entregas y diagnóstico

**Corte de auditoría:** 09/10/2026 (Uruguay). **Documento especializado**; contexto transversal en [CONTEXTO_MAESTRO_PROYECTOS.md](CONTEXTO_MAESTRO_PROYECTOS.md).

## Fuente de verdad y distinciones

- **Conductor integrado en Mapa:** `conductor/src/main/` es incorporado por `mapatrayectos/build.gradle` (tarea `prepareEmbeddedConductor`). La RELEASE principal es **Mapa R24.3 / versionCode 45**, run [37924921923](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/37924921923) **SUCCESS**. La pantalla de presupuesto/teclado se corrigió en código; **QA Samsung pendiente**.
- **Conductor APK independiente:** mismo árbol `conductor/` pero `applicationId uy.com.traslados.conductor`. En `conductor/build.gradle` está **11.5-R1 / versionCode 123**. Workflow `build-conductor-release.yml`, run [37924922002](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/37924922002) **SUCCESS**; artefacto `Traslados-Conductor-v11.5-R1-MAPA-BETA-RELEASE`, ID **11613278802**, ZIP ~21.1 MB. El workflow verifica la huella del certificado antes/después de firmar. Es una **beta**: `DemandMapActivity` muestra demanda **SIMULADA**; no presentarla como datos reales. No hay prueba física Samsung certificada.
- **DEBUG independiente desde fuente actual:** `.github/workflows/build-conductor-source.yml` (ahora etiquetado claramente DEBUG). Se actualizó en commit [6c3421f](https://github.com/marcelofgx-ctrl/traslados-android/commit/6c3421f41d02f633f24fe0449c710cdbbc5d87b2) para dejar de validar constantes antiguas **11.4-R10.4 / code 122** cuando el código fuente dice **11.5-R1 / 123**, y para nombrar inequívocamente la APK debug y generar checksum, commit y metadatos. Ejecución inicial: [38009749431](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38009749431). **Comprobar conclusión y artefacto antes de anunciar build DEBUG exitoso.** Una APK debug no se debe instalar encima de la release firmada sin considerar la firma y datos locales.

## Por qué fallaba «Build Traslados Conductor v9.2»

Run de referencia [38008909682](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38008909682), paso **Build Conductor v9.2** fallido. El YAML `build-conductor.yml` restauraba un proyecto y un paquete tar/gzip **históricos incrustados en base64**, sobrescribiendo archivos del repositorio. Al compilar fuentes actuales como `DemandMapActivity.java`, la configuración reconstruida no tenía la dependencia y paquetes MapLibre necesarios; log `:conductor:compileDebugJavaWithJavac`, `package org.maplibre.android.* does not exist` y símbolos `MapView`, `CameraUpdateFactory`, `GeoJsonSource`, etc. Es un pipeline **obsoleto**, no prueba de fallo del APK Mapa R24.3 ni de la release actual independiente.

Se retiró **solo el YAML de la rama main** en [1ca663d](https://github.com/marcelofgx-ctrl/traslados-android/commit/1ca663d6ac76c39b85dd1901221d6fcfea95f0ec). Git conserva todas sus versiones y ejecuciones previas. Puede figurar un último run fallido [38009749433](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38009749433) disparado **antes de borrar** el archivo: NO interpretarlo como regresión del Conductor actual.

## Operación segura / siguiente verificación

1. Para usar una APK independiente **instalable**, preferir release firmada y comprobar versión/artefacto/signing, no el antiguo v9.2 debug.
2. Para diagnóstico de compilación, usar el job DEBUG `build-conductor-source.yml`. Su éxito verifica Java/Gradle/APK, **no prueba flujos de reserva ni operación Samsung**.
3. La app Mapa sigue como experiencia principal; Conductor separado es respaldo. No reemplazar/borrar SQLite, PIN, SharedPreferences ni MP3 al actualizar. Nunca desinstalar para solucionar conflicto de firma sin copia de datos.
4. Probar en teléfono: abrir presupuesto con IME, visibilidad del CTA, llamadas a Supabase con PIN legítimo, notificaciones por polling; GPS mapa demanda sigue de prueba. No confundir polling de ~15 s con push FCM.
5. Para trabajo entre sesiones, actualizar contexto maestro + este documento tras cualquier nueva entrega verificada.
