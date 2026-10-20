# Quick Trip / Mapa / Conductor — instalación sin colisión de firmas
Fecha: 20/10/2026. Contrato de entrega G0–G6. Aplica a **todos** los APK futuros.

## Diagnóstico confirmado (archivo APK, no teléfono)

Los dos APK enviados como «V7 QA» se compilaron en un runner GitHub Actions con un
**certificado Android Debug generado en ese runner** (CN=Android Debug). No son una
actualización firmada con el certificado histórico del celular. La firma de ambos
APK originales enviados fue:
`86:08:02:4C:F4:92:70:EE:BC:7C:96:68:10:F7:91:88:2A:53:F7:2E:15:E7:77:47:99:45:BD:97:EF:A6:3A:CD`.

Identificadores oficiales protegidos:
- Mapa: `uy.com.mapatrayectos`
- Conductor: `uy.com.traslados.conductor`

Android exige que una actualización del mismo identificador esté firmada con
certificado compatible (y tenga versionCode permitido); limpiar cachés,
directorios sueltos o «restos» no cambia esta regla. No desinstalar ni limpiar
almacenamiento de una app operativa para probar un APK.

## Solución inmediata: APK de QA paralelos
La compilación de QA se activa con `-PquicktripQaInstallable=true`, que cambia
*solo ese build debug* a:
- `uy.com.mapatrayectos.qa` (Mapa Trayectos TEST)
- `uy.com.traslados.conductor.qa` (Conductor TEST)

La compilación normal y las release continúan con los IDs originales. Mapa TEST
y Conductor TEST se pueden instalar junto a las aplicaciones productivas.
Al ser aplicaciones diferentes, tienen su propio espacio de datos, permisos,
preferencias y notificaciones. **No heredan login, ajustes ni recorridos locales.**

IMPORTANTE: mientras GitHub Actions genere un certificado Debug distinto por
compilación, **los siguientes APK de QA no se podrán instalar encima de un QA
anterior con otra firma**. Eso exige una firma de QA duradera, configurada
mediante secretos de GitHub Actions, y no una «limpieza» recurrente. Nunca
publicar el .jks ni contraseñas dentro del repositorio.

## Procedimiento obligatorio antes de cada entrega
1. Detectar y documentar `applicationId`, versión `versionCode`,
   versión legible, certificado SHA-256 y SHA-256 del archivo.
2. Confirmar que el archivo es APK firmado válido con `apksigner verify`.
3. QA sin firma histórica: emitir `.qa` de instalación paralela y
   **dejar explícito que no reemplaza a Mapa/Conductor oficiales**.
4. Actualización oficial: requerir firma original disponible vía secretos
   y versión nueva antes de compilar. Si no, **no anunciar «actualización»**.
5. Si hay teléfono conectado con ADB, consultar primero paquetes instalados,
   firmas y versiones; detener el proceso si la firma no coincide.
6. NUNCA hacer `pm uninstall`, `pm clear`, borrar directorios privados,
   eliminar permisos ni desinstalar Mapa/Conductor como paso automático.
7. Probar APK en Android y comprobar inicio, alertas y permisos.
8. Publicar link, versionCode, package ID, tipo de firma (QA/prod)
   y advertencias de instalación en cada entrega.

## Diagnóstico opcional en PC con el teléfono conectado
Requiere depuración USB y Android SDK Platform Tools (ADB).

```sh
adb devices
adb shell pm list packages | grep -E 'mapatrayectos|traslados.conductor'
adb shell dumpsys package uy.com.mapatrayectos | grep -E 'versionCode|versionName'
adb shell dumpsys package uy.com.traslados.conductor | grep -E 'versionCode|versionName'
```

Estos comandos **solo consultan**. Para verificar el certificado de un APK ya
instalado se puede usar `adb shell pm path PAQUETE`, `adb pull` del
`base.apk` a un directorio temporal y `apksigner verify --print-certs`.
Sin acceso a ese teléfono no se puede afirmar cuál es su firma actual.
Si una instalación sigue fallando aun con paquetes `.qa`, pedir el
código exacto `INSTALL_FAILED_...` (por ejemplo desde ADB) antes de
tomar medidas; pueden existir otras causas como espacio o políticas.
