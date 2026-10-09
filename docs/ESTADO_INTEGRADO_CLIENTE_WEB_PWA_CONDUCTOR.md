# Traslados — Estado integrado: Mapa/Conductor, Cliente Android y web/PWA

**Corte:** 09/10/2026. **Propósito:** punto único de coordinación para sesiones de desarrollo de web, PWA y Android. Verificar siempre el estado actual de GitHub Actions, el código y Supabase antes de afirmar funcionalidad. Este documento no convierte pruebas de CI en pruebas físicas.

## 1. Arquitectura acordada

| Componente | Audiencia / responsabilidad | Fuente y versión |
|---|---|---|
| **Mapa Trayectos** | Única app Android principal del conductor; **Traslados Conductor está integrado como un módulo nativo interno**; mapa, jornada, recorridos, reservas, presupuestos, estados y avisos flotantes ✦ | `mapatrayectos/`, reutiliza fuentes de `conductor/` en build. Última APK **0.1-R24.3** / `versionCode 45`, GitHub Actions [37924921923](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/37924921923) **success**. |
| **Traslados Cliente nativa** | App Android independiente del pasajero: reservar, PIN, agenda, presupuestos y notificaciones locales | Fuente de reconstrucción en `cliente-pasajero/`, Workflow `.github/workflows/build-cliente-v11-5-R10.yml`. Última APK **11.5-R10** firmada RELEASE, GitHub Actions [37945735891](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/37945735891) **success**. |
| **Web de pasajeros** | Enlace HTTPS para reservas y consulta de estados | `web-pasajero/index.html`, `app.js`, `styles.css`. Publicada por GitHub Pages: https://marcelofgx-ctrl.github.io/traslados-android/ . |
| **PWA de pasajeros** | La **misma web** instalada como aplicación desde Chrome/Safari | `web-pasajero/manifest.webmanifest`, `sw.js`, iconos. **No hay APK PWA separada**, ni es un WebView. GitHub Actions Pages [37977622959](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/37977622959), intento 2, **success**. |
| **Traslados Conductor independiente** | Respaldo temporal de la pantalla de conductor durante migración | Módulo `conductor/` existente. No es otra APK requerida para el flujo actual. |

### Backend compartido y conexión

- **Única fuente operativa de reservas que DEBEN utilizar las tres interfaces:** proyecto Supabase **`zetaudvvutlouiqxopvg`**, endpoint HTTPS `https://zetaudvvutlouiqxopvg.supabase.co`. No crear/copiar reservas falsas y no borrar ni migrar datos reales sin aprobación explícita.
- La app nativa Conductor y la app nativa Cliente ya consultan las RPC de ese proyecto. La nueva web/PWA de GitHub Pages está configurada para consultarlas también. La integración total web ↔ Android todavía necesita prueba de extremo a extremo, incluyendo creación real, aceptación, presupuesto y cambio de estado.
- Las apps **no se comunican directamente entre sí ni por conversaciones de ChatGPT**: leen/escriben **las mismas reservas en el mismo backend mediante RPC autenticadas**. Para mostrar cambios sin esperar, necesitamos refresco/eventos autorizados (Realtime o notificaciones push).
- La antigua web en Lovable, proyecto **Ride Bookings**, ID `37e5ea75-667b-4c05-b55b-52dddb9e82f3`, llegó a tener **otro Supabase/Lovable Cloud (`xetklwcxebcpnkrdrmaa`)**. **NO asumir** que Lovable web está sincronizada con la operativa real: verificar todas sus rutas/funciones antes de publicarla como web oficial. **La web/PWA en GitHub Pages** es actualmente la ruta publicada por nuestro workflow. Mantener dos formularios públicos que escriban en proyectos diferentes produciría reservas perdidas.
- No introducir service_role, contraseñas ni claves secretas en código cliente. La clave `sb_publishable_...` pública es apta para frontend **solo con RLS/RPC seguras correctamente configuradas**. Los PIN/sesiones de clientes son privados; jamás compartir tokens en URL de acceso abierto ni logs públicos.

### Funcionalidades RPC y estados para pruebas de integración

- Cliente web invitado: `create_reservation` y `get_reservation_by_token` con token privado devuelto; autenticado: `customer_register`, `customer_login`, `customer_create_reservation_v12`, `customer_list_reservations_v12`, consulta de disponibilidad y presupuesto (`customer_quote_decision_v11_4`).
- Conductor: `driver_list_reservations_v2`, `driver_set_status_v2` y funciones de presupuesto existentes.
- Estados operativos de reservas incluyen `PENDIENTE`, `PRESUPUESTO_ENVIADO`, `ACEPTADA`, `ACEPTADA_CLIENTE`, `CONFIRMADA`, `EN_VIAJE`, `FINALIZADA`, `CANCELADA`, `RECHAZADA`, `RECHAZADA_CLIENTE`. No saltar pasos ni fabricar estados.
- Montevideo y Canelones destacados más 17 departamentos restantes; direcciones con coordenadas válidas; paradas intermedias hasta 8 donde backend/autenticación lo permite; opciones "Ahora", "+10 min" y "Programar". Verificar horarios en zona `America/Montevideo`. **La reserva NO confirma la disponibilidad hasta respuesta del backend/conductor.**
- **No está implementado/probado un seguimiento GPS vivo para el pasajero ni push FCM completos**. El servicio Android Conductor consulta Supabase aproximadamente cada 15 segundos mientras Android lo mantiene vivo. No prometer entrega garantizada si se cierra/fuerza detención. Web PWA no crea reservas offline.

## 2. Descargas y respaldos verificados

**Mapa Trayectos** APK firmadas, archivos Drive (cuenta propietaria): 
- **R24.3** https://drive.google.com/file/d/1ySFGkn8pRaZgrQ1xgM7GQM9FcvNXifrX/view
- **R24.2** https://drive.google.com/file/d/1XwaSF9jkxDJKmjhNh5ltD9B8g-IXf1le/view
- **R24.1** https://drive.google.com/file/d/1spEV5CniaJIm16xCIAjH6M8oDctae53w/view
- **R24.0** https://drive.google.com/file/d/1Kl3JMiW-GPcUZh_Hsm4EEgcDAhT0BIz0/view
- R23.1 https://drive.google.com/file/d/1WLYfqWfyt-VDvdij_WSUc1MpR_0xTrsm/view
- R22.9 https://drive.google.com/file/d/1_OKMbJM5qNKVSjnocq4Et-5Lgzjvgy7q/view

**Cliente Android**:
- **R10 — APK directa para Android (recomendado):** https://drive.google.com/file/d/1zmINKO6Xcjc6muVpRgwBHZ5uC2s2NFUV/view . **R10 ZIP** con APK original firmada y fuente: https://drive.google.com/file/d/1mZFu2Xl21v0HhHvPJQ4IpbHzqL3MVP0D/view . APK `Traslados_Cliente_v11.5_R10_RELEASE.apk`; su SHA-256 es `59c24c33c83f9a19648ca1cf280d64b5167995751cc663965d2b4a1fb1d5e5f4` (verificado con `SHA256SUMS.txt`).
- R9.1 DEBUG anterior ZIP https://drive.google.com/file/d/1ZLB9m6-tEL6-HEBlxjJMZ_p6n25ECOSR/view . Firma debug R9.1 puede no ser compatible con R10 RELEASE; antes de desinstalar, verificar datos privados locales.
- **Carpeta Cliente/PWA:** https://drive.google.com/drive/folders/14gneZ3OjDIxXjH211moSdu7Vr9hbDBiZ

**Web / PWA**:
- Sitio HTTPS: https://marcelofgx-ctrl.github.io/traslados-android/
- Código y PWA completos ZIP https://drive.google.com/file/d/1Z4R_VAr_tmROGn84RC81L-X693aYvRLk/view . Para usar PWA no se descarga ZIP: abrir web HTTPS en Chrome y pulsar «Instalar»/«Agregar a pantalla de inicio».

## 3. Checklist de coordinación obligatoria web ↔ APK Cliente ↔ Mapa

1. **Verificar en el código actual** de Lovable y GitHub Pages la URL del Supabase y las RPC. El proyecto en producción debe usar `zetaudvvutlouiqxopvg`. Evitar dos bases distintas.
2. **Prueba completa (sin modificar esquema):** crear UNA reserva de prueba controlada con Cliente nativa → observarla en Conductor dentro de Mapa → presupuestar y responder desde Cliente → comprobar mismo código/estado en web/PWA con sesión. Repetir invirtiendo origen: crear desde web → ver en Conductor → gestionar → observar en Mis traslados. No llenar producción de pruebas ni ejecutar estas operaciones sin autorización cuando implican cambiar datos reales.
3. Comprobar filas, permisos RLS, seguridad de RPC, no exponer número/teléfono a otros usuarios, impedir dobles envíos, precisión de GPS/ubicaciones, tiempo local Uruguay, accesibilidad/teclado y experiencia móvil. Documentar fallos antes de afirmar "funciona".
4. Cuando se cambie el esquema/RPC, actualizar **las tres superficies** coordinadamente, versionar y probar compatibilidad hacia atrás. Mantener una única tabla de estados y campos.
5. Para alertas confiables, implementar notificaciones FCM + servidor seguro, con suscripción y permisos por dispositivo. Para ver posición en tiempo real, publicar coordenadas autorizadas solo para reserva activa y destinatario validado; nunca exponer ubicación de conductor fuera de viajes autorizados. No se implementó hasta este corte.
6. Publicar Web/PWA vía workflow `.github/workflows/deploy-web-pasajero-pwa.yml`; publicar Cliente mediante `.github/workflows/build-cliente-v11-5-R10.yml`; publicar Mapa mediante `.github/workflows/build-mapa-trayectos.yml`. **No confundir éxito de build con calidad probada en Samsung**.

Este documento es la referencia compartida entre sesiones. En trabajos posteriores, actualizar este mismo archivo y `docs/RETOMAR_MAPA_TRAYECTOS.md` con versiones y enlaces nuevos.
