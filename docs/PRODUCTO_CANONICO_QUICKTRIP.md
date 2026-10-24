# Quick Trip — contrato canónico de producto y diseño (FASE 1)

**Fecha de corte:** 2026-10-24  
**Base inmutable de referencia:** `main@888d0696ebff98773e61039e3df6e46efd872317`  
**Rama aislada:** `qa/quicktrip-blindaje-fase1-canonico-20261024`  
**Estado:** CONTRATO DE RECUPERACIÓN DOCUMENTADO — **NO ES DISEÑO PUBLICADO NI APROBACIÓN FÍSICA**.

> **Precedencia:** decisiones de producto confirmadas > documentos posteriores específicos > documentos anteriores históricos. Un test de Chromium `SUCCESS` no autoriza una interfaz ni la convierte en la captura visual aprobada.

## 1. Producto oficial, sin excepciones

Solo existen estas tres superficies oficiales:
1. Página web pública (presentación).
2. Web/PWA de pasajeros **única**, idéntica en Chrome e instalación PWA.
3. **Una sola APK Android Mapa Trayectos con Conductor integrado**; no crear, ofrecer ni compilar APK Conductor o APK Cliente independientes.

**Backend:** Supabase Traslados. Esta fase **no lo consulta ni modifica**, ni realiza reservas, migraciones o despliegues.

## 2. Contrato vigente de portada

- Marca **Quick Trip**, con pequeño acento superíndice `go`, logo legible en tema claro y oscuro.
- Escena de ciudad al fondo, paleta petróleo profundo/champagne; cambio visible **claro/oscuro**.
- **Exactamente tres servicios con accesos circulares**: **Tu Viaje**, **Tu Paquete**, **Tu Viaje Programado**.
- «Viajar ahora» **no** es un cuarto acceso. No restituir tarjetas rectangulares antiguas, menú viejo ni héroe «Tu próximo viaje empieza acá» como sustituto de la referencia aprobada.
- Tu Paquete puede expresar su estado real sin inventar funcionalidad.

Referencias de intención: `docs/QUICK_TRIP_V6_G4_FUNCIONAL_20261019.md`, `docs/QUICK_TRIP_V6_IMPLEMENTACION_VISUAL_20261019.md`. El laboratorio V6 es una referencia compositiva, **no un motor de reservas ni una certificación de fidelidad 1:1**.

## 3. Contrato vigente de Tu Viaje

La decisión más reciente, `docs/TU_VIAJE_PANTALLA_UNICA_CONTRATO_20261020.md`, reemplaza **para el nuevo flujo inmediato** la composición antigua de cuatro pantallas del 19/10:

- **Una sola pantalla compacta**, con cabecera Quick Trip que no invade barra Android.
- Origen, destino, paradas, búsqueda/autocompletado, GPS y handlers **originales**.
- **Un mapa original**, traza vial comprobada, km/duración/precio **verificados**. No fabricar ETA, cotizaciones ni puntos GPS.
- Diferenciar **conductor→recogida** de **pasajero→destino**.
- Historial plegable pequeño, cuenta y pasajeros, selección de horario sin pantalla kilométrica.
- **Un solo CTA «Solicitar viaje»**, sobre el motor y la idempotencia existentes; si la ruta no es verificable, bloquear envío.
- Petición inmediata: el Conductor **acepta o rechaza**, sin fase de presupuesto.
- Viaje Programado: **flujo separado**, conservando los contratos reales vigentes.
- Botón Atrás, recarga, reanudación, caché PWA y teclado Samsung no pueden provocar pérdida de datos inesperada ni duplicar reservas.

El contrato `docs/CONTRATO_VISUAL_TU_VIAJE_4_PANTALLAS_20261019.md` sigue archivado como antecedente histórico **sin ser la plantilla del nuevo Tu Viaje compacto**.

## 4. Una propiedad visual, no capas históricas

**Destino de saneamiento futuro:** una sola implementación de presentación y una sola hoja de estilos canónica, sin apilar `!important` sobre V5/V6/V7/v85. Los motores de `app.js`, rutas, búsqueda, reservas, ETA, RPC y Android permanecen intactos.

**Importante:** `main` actual todavía contiene `quicktrip-clean.css`, `quicktrip-clean.js` y el caché V83. Están inventariados como estado de código **NO como el diseño final**. Esta fase no los cambia ni los oculta.

La recuperación **no** implica fusionar todas las ramas QA ni tomar como «última» la que tenga número de versión mayor. Elegiremos diffs mínimos sobre la base, con pruebas antes/después. Los PR Android divergentes quedan fuera de esta fase.

## 5. Pruebas de aceptación separadas — no confundir hitos

| Etapa | Evidencia obligatoria | ¿Cumplida en fase 1? |
| --- | --- | --- |
| Integridad de base | SHA de main y hashes Git de motores protegidos | **Verificable** mediante script de fase 1 |
| Contrato | Manifiesto coherente, sin cuarto servicio ni 4 etapas antiguas | **Verificable** mediante script de fase 1 |
| Bloqueo CI | Una prueba positiva y negativas que produzcan rechazo | **Verificable** mediante CI de fase 1 |
| Fidelidad visual | Imágenes privadas de referencia + SHA256 + comparaciones 360/393/412, claro/oscuro | **PENDIENTE** |
| Funcional end-to-end | Búsqueda, rutas, ETA, paradas, historial, submit único; backend sintético | **PENDIENTE** en la futura candidata |
| PWA | SW e invalidación de caché, recarga y estado de nueva versión | **PENDIENTE** en la futura candidata |
| Samsung físico | Captura de la versión correcta, gesto Atrás, instalación, reapertura y scroll | **PENDIENTE** |
| Publicación | PR, required checks realmente exigidos y despliegue del SHA aprobado | **NO AUTORIZADA** |

## 6. Regla estricta para fuentes visuales

Las capturas originales se citan en `docs/CONTINUIDAD_METODOLOGIA_Y_CAPTURAS_20261018.md`, pero **no están localizadas con huella comprobada en esta auditoría**. Pueden contener información privada, por lo que **no se suben al repositorio público**.

El estado de evidencias en `docs/quicktrip-canonical-contract.json` permanece:
- `original_visual_baselines: not_located_in_accessible_sources`
- `reference_image_sha256: []`
- `real_samsung_validation: pending`
- `release_approved: false`

**La compuerta de promoción debe fallar** mientras falte esa evidencia. Las capturas QA generadas con Chromium, las imágenes generadas y los renders de V6 **no reemplazan la aprobación física**.

## 7. Cómo continuar sin burocracia

Esta es **la única** rama de fase 1. La ejecución de sus pruebas no requiere aprobar cada archivo. Entregar un cierre único con: SHA de la rama, hashes de motores, ejecución CI, tests positivos/negativos y lista de pendientes.

La próxima fase, si fuera autorizada, integrará la presentación visual en **otra** rama aislada a partir de la base verificada, sin desplegar, y exigirá comparar contra referencias exactas antes de enseñar un enlace al Samsung.

**No modificar en fase 1:** `main`, backups, motores, `mapatrayectos/`, `db/`, otras ramas/PR, repositorio backend, Supabase, CI productivo, Pages, releases, Android/APK, correos ni configuraciones GitHub.
