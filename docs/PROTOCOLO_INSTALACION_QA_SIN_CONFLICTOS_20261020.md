# Protocolo de instalación Android — Quick Trip QA (20/10/2026)

## Problema detectado

Los APK debug de Mapa Trayectos y Traslados Conductor publicados anteriormente
utilizaban los identificadores de paquete de producción:
`uy.com.mapatrayectos` y `uy.com.traslados.conductor`. Android rechaza una
actualización cuando la firma de la aplicación instalada no coincide con la
firma del APK nuevo (por ejemplo, claves debug distintas). El mensaje visible
puede limitarse a «No se instaló la aplicación».

Una firma antigua **no es un archivo residual que podamos barrer**. Es parte
de la identidad de la aplicación instalada. Las reinstalaciones no deben
borrar automáticamente bases, preferencias, PIN, historial, GPS ni reservas.

## Solución obligatoria para QA

El workflow `.github/workflows/quicktrip-v7-apks.yml` define
`QUICKTRIP_QA_INSTALL_ID=r<GITHUB_RUN_ID>` para compilar ambos módulos.

| Variante | Identificador Android | Nombre visible |
|---|---|---|
| Mapa oficial | `uy.com.mapatrayectos` | Mapa Trayectos |
| Mapa QA | `uy.com.mapatrayectos.qa.r<ID>` | Mapa QA |
| Conductor oficial | `uy.com.traslados.conductor` | Traslados Conductor |
| Conductor QA | `uy.com.traslados.conductor.qa.r<ID>` | Conductor QA |

Cada compilación QA utiliza un identificador **único**, por lo que nunca
intenta actualizar una aplicación que esté firmada con otra clave.
Los nombres e identificadores de producción se mantienen intactos.

Antes de aceptar un artefacto, el workflow:
1. Compila Conductor y Mapa con el mismo código de negocio de siempre.
2. Revisa con `aapt dump badging` el identificador Android **exacto**,
   además de la etiqueta QA.
3. Valida la firma APK con `apksigner verify --print-certs`.
4. Genera hashes SHA-256 y un archivo `BUILD-INFO.txt` con paquetes y
   número del workflow.
5. Falla cerrado si algún paquete no corresponde al QA exclusivo.
6. Publica las dos APK en un artefacto de GitHub Actions.

El control local opcional `scripts/android_qa_install_preflight.sh`
permite repetir la verificación y, con ADB conectado, enumerar
instalaciones previas **sin eliminar ninguna**.

## Instalación en Samsung

1. Descargar la APK nueva etiquetada QA del último workflow correcto.
2. Instalar desde Archivos / Chrome; permitir la instalación desde esa fuente
   en los ajustes si Android lo solicita.
3. Abrir **Mapa QA** o **Conductor QA**, no la aplicación oficial.
4. No desinstalar ni eliminar datos de la aplicación operativa.
5. Detener/pausar monitores de la otra variante al probar reservas para evitar
   notificaciones simultáneas; ambas pueden consultar el **mismo** Supabase.
6. Las QA antiguas se pueden desinstalar más tarde desde Ajustes > Aplicaciones,
   individualmente y **solo si el usuario confirma que no necesita sus datos**.

> Esto evita conflictos de paquete y firma. No garantiza la instalación frente
> a archivos corruptos, espacio insuficiente, políticas de seguridad o permisos.
> Si el error persiste, obtener el código real de Android con:
> `adb install /ruta/QuickTrip-Mapa-V7-QA.apk`.
> No ejecutar `pm clear`, `adb uninstall` ni barridos globales por defecto.

## Firma de actualizaciones oficiales

Para actualizar una APK oficial *sin perder datos*, conservar en todas las
versiones el **mismo applicationId**, **la misma clave de firma** y aumentar
`versionCode`. La clave de producción debe almacenarse en secretos seguros
del CI; si no está disponible, **bloquear la publicación oficial**.
Nunca reemplazarla silenciosamente por una clave debug.

## Web y PWA

La Web/PWA Quick Trip se instala desde el navegador. Su service worker,
almacenamiento e identidad de instalación son independientes de la firma de
Mapa/Conductor. Limpiar datos del navegador **no arregla** una firma de APK
incompatible y puede perder el estado local. No hacerlo como rutina.

## Criterio de aceptación

- QA instala junto a la app original sin desinstalación previa.
- Ningún archivo ni preferencia de producción se borra durante QA.
- Dos compilaciones QA sucesivas no chocan por firma.
- Se entrega la APK y sus metadatos y hashes.
- Los motores de búsqueda, ruta, GPS, reservas y backend no se modifican.
- El procedimiento de limpieza, si alguien lo pide, es manual y específico,
  nunca automático ni masivo.
