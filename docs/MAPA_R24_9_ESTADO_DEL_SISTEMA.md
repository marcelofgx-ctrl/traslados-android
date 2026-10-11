# Mapa Trayectos R24.9 — Estado del sistema

## Objetivo
Nuevo acceso **☰ Menú principal → ◈ Estado del sistema** dentro de la APK nativa Mapa Trayectos, sin cambiar la PWA Cliente ni Conductor independiente.

## Contenido de la pantalla
1. **Almacenamiento de Supabase:** lectura del backend del tamaño global aproximado de las bases PostgreSQL del proyecto, indicador de porcentaje y remanente contra cuota de referencia **Free: 500 MB**, semáforo: verde (<65 %), ámbar (65–84 %) y rojo (≥85 %). La cuota del plan es una **referencia** fijada en el backend y debe actualizarse si cambia el plan; no equivale al espacio de WAL/disco físico.
2. **Información remota privada** (solo PIN válido): recuentos de reservas, abiertas, trayectos, jornadas, puntos GPS, dispositivos y jornadas sin cierre; última fecha de muestra GPS, **sin coordenadas**.
3. **Base local SQLite:** recuentos de jornadas, viajes, puntos, viajes y jornadas por sincronizar; tamaño físico SQLite + WAL del teléfono. **No confundir estos datos con los de la nube**: el teléfono puede guardar información previa al reinicio del 11/10/2026.
4. **Operación:** conectividad Android validada, jornada/viaje en estado local, permiso de disponibilidad pública; ninguna de estas señales es garantía de ETA futura o de agenda desocupada.
5. **Versión de la APK** y Android.
6. **Actualizar diagnóstico**: actualización manual y en segundo plano, con estado de fallo explícito y sin mostrar métricas remotas anteriores como si fueran actuales.

Diseño oscuro petróleo, texturas existentes `TexturedDrawable`, bordes dorados, tarjetas premium compactas; una sola pantalla ScrollView. No usa servicios de pago, credenciales administrativas, ubicaciones de clientes, geocodificación ni envío de GPS nuevo.

## Servidor (ya migrado a Supabase de Traslados)
Nueva función `public.driver_system_status_v1(p_pin text) RETURNS jsonb` bajo SQL migration `driver_system_status_v1_pin_guarded`. `SECURITY DEFINER`, `search_path=''`, nombres de tablas y procedimientos totalmente cualificados. **Primero** verifica `driver_pin_valid`, y ante PIN incorrecto lanza SQLSTATE 28000 sin devolver métricas. El cliente usa la clave pública publishable estándar del proyecto; NO se expone clave service role en APK. `EXECUTE` para roles de acceso vía RPC pero información condicionada al PIN del conductor. La función es solo lectura.

El cálculo remoto usa la suma `sum(pg_database_size(datname))` para las bases Postgres del mismo proyecto (cifra conservadora frente a `pg_database_size(current_database())`). La cuota de 500 MB se etiqueta explícitamente como referencia del plan Free, no como consulta en tiempo real del plan a la API de facturación.

**Prueba de seguridad:** se confirmó que `driver_system_status_v1('0000')` rechaza el acceso con SQLSTATE 28000. No se usó ni extrajo ningún PIN de conductor en la comprobación.

## Archivos APK
- `SystemStatusActivity.java`: interfaz + red en segundo plano + tarjeta de Supabase + SQLite local + estado local de jornada y permisos.
- `SystemStatusPolicy.java`: presentación/umbrales en Java puro.
- `Api.java`: RPC `getSystemStatus(pin)` por HTTPS.
- `MainActivity.java`: acceso al menú principal.
- `AndroidManifest.xml`: actividad interna no exportada.
- `build.gradle`: `versionCode 51`, `versionName 0.1-R24.9`, preservando `applicationId='uy.com.mapatrayectos'` y certificado de firma habitual.
- `tests/SystemStatusPolicyTest.java`: test Java independiente y verificación en CI `validate-modular-mapa.yml`.

## Limitaciones y QA móvil
- La APK contiene únicamente cifras en el instante de la consulta (sin monitorización intensiva en segundo plano).
- Si Conductor no tiene PIN guardado en esta instalación, el panel solicita ingreso manual. No guarda ese PIN como parte de la nueva pantalla.
- La primera consulta de nube real desde Samsung con PIN legítimo requiere comprobación manual; no se simula ningún viaje ni reserva.
- Un cambio de plan en Supabase requiere adaptar `database_quota_bytes` en backend.
- Verificar en Samsung: abrir Menú → Estado del sistema, comprobar acceso seguro y que no aparece estado global con PIN incorrecto; entrar con PIN válido; observar base local vs nube y prueba sin Internet; volver al mapa sin alterar GPS/jornada. La publicación/instalación deben validarse por CI firmado antes de anunciar APK descargable.

## Notas operativas
Después del reinicio del 11/10 la base PostgreSQL contiene respaldos privados, por lo que **el espacio usado no es cero aunque no haya reservas o trayectos visibles**. La retención y archivo de los puntos GPS históricos sigue siendo una decisión pendiente; por solicitud expresa se mantiene íntegra la toma de GPS, incluidos tiempos detenido.
