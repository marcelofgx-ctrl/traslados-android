# Quick Trip v85 corregida — validación Samsung física sin despliegue

Fecha: 24/10/2026. Rama QA: `qa/quicktrip-v85-samsung-pwa-validation-20261024`.
**No es producción. No es una APK. No fusionar ni desplegar.**
Este protocolo es para el teléfono Samsung físico. Las pruebas Chromium emuladas
están separadas y nunca deben anunciarse como pruebas Android reales.

## Resumen de la validación automatizada

- Perfil emulado: Android 16 / SM-S918B; 393×852 y 360×780, táctil
- Hardware físico: NO conectado al CI; solo comprobable siguiendo este protocolo
- Back del navegador con `history.back`: PASS
- Borrador después de recargar el mismo documento: PASS
- Nueva pestaña tras cerrar la anterior: sesión ficticia presente, **borrador no preservado**
- A 360×780 el CTA queda debajo del footer visible y exige desplazamiento
- Cache: PASS con Service Worker Chromium real tras detener el servidor HTTP
- Total controles reales hacia Supabase: 0; reservas reales: 0

## Preparación física segura, solo con PC + cable USB

No abrir GitHub Pages ni la PWA productiva para esta prueba: todavía
son otras versiones. Para probar **exactamente esta rama**, se necesita
un servidor **local**, no un despliegue público.

1. En la PC instalá Git, Python 3 y Android Platform Tools (ADB).
2. Abrí una terminal nueva y descargá **solo esta rama**, sin reemplazar el proyecto local:

```sh
git clone --single-branch --branch qa/quicktrip-v85-samsung-pwa-validation-20261024 https://github.com/marcelofgx-ctrl/traslados-android.git quicktrip-v85-samsung-qa
cd quicktrip-v85-samsung-qa
python3 web-pasajero/tests/quicktrip-v85-samsung-local-preview.py
```

En Windows usar `py -3` en lugar de `python3` si corresponde.

3. En el Samsung habilitá **Opciones de desarrollador** y **Depuración USB**.
   Conectá el cable y aceptá en el teléfono la autorización de depuración.
   Esto no instala ni cambia ninguna aplicación.
4. En otra terminal de la PC, con ADB disponible:

```sh
adb devices
adb reverse tcp:8139 tcp:8139
```

5. En **Chrome del Samsung** abrí exactamente:

```
http://localhost:8139/web-pasajero/
```

6. Verificá el distintivo **«QA LOCAL · SOLO LECTURA · SIN BACKEND»**.
   Si no aparece, **detené la prueba**: puede ser otra versión.
   La página se sirve por USB desde tu computadora y el navegador bloquea
   solicitudes externas (incluido Supabase). El envío está deshabilitado
   mediante el modo de vista previa ya presente en app.js.

Si Chrome ofrece «Agregar a pantalla principal» / «Instalar app», se puede
instalar **solo esta PWA de localhost** para probar la reapertura.
Que aparezca la opción depende del Chrome/Android físico. No instalar una APK.

## Pasos de prueba y qué observar

| Nº | Acción en el Samsung | Resultado esperado |
|---|---|---|
| 1 | Abrí la URL y comprobá Inicio | Tres accesos: Tu Viaje, Tu Paquete y Tu Viaje Programado |
| 2 | Entrá en Tu Viaje | Una pantalla, campos de origen/destino, mapa, horario y botón deshabilitado de solicitud |
| 3 | Usá el gesto Atrás del sistema Android | Regresa a Inicio sin cerrar la PWA |
| 4 | Entrá de nuevo y elegí **ubicaciones ficticias disponibles** en el buscador (por ejemplo Aeropuerto si aparece); completá 2 pasajeros e indicaciones «QA prueba» | Borrador visible; no ingresar dirección, teléfono ni identidad real |
| 5 | Capturá pantalla; Chrome ⋮ → Actualizar/Recargar | Recupera origen, destino, pasajeros e indicaciones. Las rutas/ETA/importe reales NO están activos en esta vista segura |
| 6 | Abrí la vista de horarios y cerrala | Se mantiene la pantalla compacta, sin salto ni superposición permanente |
| 7 | Abrí **Recientes**, descartá Chrome/PWA, volvé a abrir el icono o la URL | Anotá si el borrador reaparece **sí/no**. Esta prueba es especialmente importante: en una pestaña nueva de Chromium no se recuperó |
| 8 | Con PWA abierta, **detené el servidor Python** en la PC (Ctrl+C), y luego recargá | Se muestra pantalla de falta de conexión; no hay solicitud ni envío pendientes |
| 9 | Reiniciá el servidor, recargá | Vuelve la interfaz; no se creó una reserva |

Para la prueba de desconexión **no basta activar Modo Avión**:
`adb reverse` puede continuar sirviendo localhost por el cable USB.

**Precauciones:** nunca tocar «Solicitar viaje» si el distintivo QA desaparece.
Este entorno es de lectura, y todas las peticiones externas se bloquean; por
eso el precio y la ETA reales no forman parte de la prueba física local.

## Evidencia para recopilar

Capturas recomendadas: portada, formulario lleno con datos ficticios,
formulario después de Recargar, vista tras gesto Atrás, vista tras cerrar/reabrir
y pantalla sin servidor. Adjuntá además si Chrome ofreció instalación PWA
y si utilizaste Chrome normal o un icono instalado.

## Registro mínimo para responder en ChatGPT

- Atrás Android: **Sí / No** volvió a Inicio.
- Recargar: **Sí / No** conservó recorrido, pasajeros e indicaciones.
- Cerrar/reabrir: **Sí / No** conservó borrador.
- Sin conexión (servidor detenido): **Sí / No** mostró vista offline.
- Pantalla: **Sí / No** el botón quedó tapado por barra inferior.

## Cierre seguro de la prueba

En la PC ejecutá `adb reverse --remove tcp:8139` y detené
Python con Ctrl+C. Si instalaste el acceso localhost, quitá **solo ese
acceso de prueba** y borrá datos del sitio `localhost:8139` en Chrome.
No tocar instalaciones existentes de Quick Trip/Mapa, ni limpiar datos
del dominio productivo. La rama queda sin fusionar.

## Protección técnica del entorno

`web-pasajero/tests/quicktrip-v85-samsung-local-preview.py` solo escucha
en `127.0.0.1`, inyecta el meta `traslados-preview=read-only` de app.js,
impone CSP `connect-src 'self'`, deniega POST/PUT/DELETE y añade el distintivo QA.
Las pruebas automatizadas de red interceptan o abortan todos los dominios
externos. No accede a Supabase real ni crea reservas.
