# Mapa Trayectos — Datos, exportación y conservación (17/10/2026)

## Decisión de producto

No borrar recorridos ni coordenadas GPS. Incluso fuera de un viaje, las muestras GPS permiten reconstruir la jornada completa (trayectos vacíos, tiempos de espera, búsqueda, retorno a casa). El detalle tiene valor futuro para rentabilidad y análisis de zonas. **No hay retención automática ni purga**.

Separamos:
- **Configuración**: permiso de globito, pruebas, sonidos. Credenciales, parámetros, matrícula, tarifas y cuentas permanecen como están.
- **Datos**: exportar base local, importar base local, exportar historial de Supabase, consultar espacio/estadísticas. No se expone «Empezar de cero».

## Exportación local (existente, intacta)

`DatabaseBackup.exportBackup` crea ZIP con SQLite + manifest SHA-256; se puede restaurar localmente con `DatabaseBackup.importBackup`. No exporta automáticamente el contenido remoto de Supabase.

## Exportación nube (nueva)

`CloudDataBackup` toma un corte temporal y consulta un nuevo RPC de lectura en bloques de 800, generando un ZIP con JSONL independiente por categoría y manifest. Respalda:
- `mapa_trayectos_shifts`, `mapa_trayectos_trips`, `mapa_trayectos_points`;
- `reservations`, `reservation_events`, `reservation_stops`, `reservation_chat_messages`.

**No** incluye `driver_access`, `driver_settings`, `customers`, dispositivos/secretos, PIN, sesiones, suscripciones push, ni archivos de la aplicación. Esta exclusión es deliberada: protege configuración y credenciales. El ZIP remoto **es un archivo histórico, no es todavía restaurador automático**.

La RPC `driver_data_export_page_v1` valida PIN conductor **y** `device_id + device_secret` previamente registrados, restringe tabla a una lista fija de siete conjuntos y limita a 1000 filas por página. No acepta SQL libre, no ejecuta DELETE/UPDATE/TRUNCATE. Se instala únicamente en proyecto Supabase Traslados; no toca Oak & Ember.

Seguridad operativa: exportar con jornada cerrada, porque el archivo por bloques no es una instantánea ACID multi-tabla. No guardar el ZIP con datos privados en repositorios GitHub públicos ni reenviarlo por WhatsApp; mover a almacenamiento privado. El PIN no se incluye en el ZIP ni se registra en logs.

Verificación: el exportador relee el ZIP escrito y comprueba formato, entradas, conteos exactos por dataset y SHA-256 de todo el archivo. En caso de error o cancelación, descartar el ZIP parcial; **ningún dato de Supabase cambia**.

## Acceso a datos

Mapa Trayectos → menú principal → **Datos**:
1. Exportar base local
2. Importar base local
3. Exportar historial de nube
4. Espacio y estadísticas

La opción **Configuración** abre controles de permisos/globito y sonidos. El panel «Estado del sistema» mantiene su lectura de uso global de base y conteos locales.

## Pruebas y promoción

- Rama de integración: `feature/mapa-datos-sin-borrado-20261017`; PR #110, borrador.
- CI compila una APK de revisión independiente `uy.com.mapatrayectos.datosreview`; no altera la APK instalada ni publica automáticamente en main.
- La RPC de nube se aplicó de manera no destructiva, con verificación negativa de autenticación.
- Se comprobó que existen todavía 80.563 puntos GPS y 8 reservas después de aplicar la RPC.
- Pendientes: compilación Android, prueba física Samsung, exportar ZIP real con PIN y comprobar el checksum/archivo, validar conteos con servidor sin modificar datos.
- No fusionar, desplegar versión principal ni habilitar purga sin QA y petición explícita.
