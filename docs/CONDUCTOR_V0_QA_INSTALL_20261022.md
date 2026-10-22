# Quick Trip — Mapa Trayectos Conductor V0 APK QA

Fecha: 22 de octubre de 2026. Rama: `feat/mapa-conductor-v0-qa-apk-20261022`.

## Aprobación y restricciones
Solicitada una APK de prueba de la **misma aplicación Mapa Trayectos**, con Conductor V0 integrado. Solo solicitudes ficticias; no cambiar Supabase, producción, ni fusionar a main o modificar motores estables.

## Instalación segura
- `applicationId = uy.com.mapatrayectos.qa.r78`, diferente de la APK normal `uy.com.mapatrayectos`. Es **una variante QA del mismo Mapa**, no una app independiente Conductor.
- Nombre del launcher `Mapa Trayectos QA`; se instala en paralelo, sin desinstalar versiones existentes.
- Gradle `assembleDebug`: firma debug Android. No es la misma firma de release. Este aislamiento de `applicationId` evita conflicto de firma y datos.
- El AndroidManifest de debug retira el permiso INTERNET para bloquear todas las operaciones de red, incluso de componentes del Mapa que existan pero no se modifican. Resultado: **el mapa puede aparecer sin teselas online**; la demostración Conductor V0 sí funciona con datos sintéticos.
- Conductor V0 se abre deslizando desde el borde DERECHO hacia la IZQUIERDA en la pantalla de Mapa. Cerrar deslizando en sentido contrario o atrás. La pantalla principal carga el binding debug-only; Release continúa inerte.
- Dos solicitudes nuevas, una aceptada, una programada excluida del flujo de atención directa. Pulsar Aceptar/Rechazar actualiza solo memoria. El botón "Ver histórico de prueba" muestra registros ficticios y estados.
- Ningún PIN, token, petición ni reserva real se carga en el panel QA. Android puede mostrar solicitudes de permiso GPS propias del Mapa sin que esta versión pueda subir información por red.

## Pruebas / verificación
El workflow independiente ejecuta el contrato JVM original, compila `assembleDebug`, confirma el nombre del paquete, la firma Android, etiqueta QA y comprueba que el manifest final NO contiene permiso INTERNET. Se publica solamente un artefacto GitHub Actions para descarga.
El APK NO se firma con certificado de producción; el usuario deberá instalarla sin desinstalar su Mapa actual. Al tener paquete nuevo, no comparte datos ni preferencias.

## Limitaciones
- No demuestra autenticación con Supabase real, recepción de viajeros ni integración operativa. Eso requiere aprobaciones posteriores.
- Si no hay conexión, el mapa base/map tiles remotos no cargarán, por diseño y seguridad.
- La prueba del proceso de instalación física en el Samsung no es posible desde CI; solo se verifica firma y archivo Android.

## Rollback
Desinstalar `Mapa Trayectos QA` exclusivamente; no tocar `Mapa Trayectos`. Para el código, volver a la rama base `257d3550d35e9659791588f52fa3b4cf76b222d5`. No hay migraciones ni datos productivos.
