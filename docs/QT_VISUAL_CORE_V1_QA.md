# QT Visual Core v1 — etapa 1: auditoría y contratos QA

Fecha: 26/10/2026 (America/Montevideo).
Repositorio: marcelofgx-ctrl/traslados-android.
Rama autorizada: qa/quicktrip-v90-acceleration-pilot-20261026.
SHA de partida inmutable: d92cf74f364035aca4f47c8cd88c73d99e3f84ac.
Estado: inventario y pruebas preparadas; NO constituye aprobación visual, QA Samsung, entrega de APK ni publicación.
Alcance: documentación y un nuevo test QA reutilizando Node + Playwright Core ya presentes; cero cambios a motores, CSS, HTML, Java, backend, workflows, Supabase, producción o APK.

## 1. Fuentes y precedencia

1. docs/PRODUCTO_CANONICO_QUICKTRIP.md: tres superficies; portada de tres círculos y Tu Viaje compacto; no restaurar cuatro pasos antiguos.
2. docs/TU_VIAJE_PANTALLA_UNICA_CONTRATO_20261020.md: origen/destino, mapa real, precio/ETA comprobados, historial plegable y botón de solicitud original.
3. docs/quicktrip-canonical-contract.json: fuentes protegidas y condición de falta de referencias originales.
4. docs/INVENTARIO_FUENTES_QUICKTRIP_20261024.md: distingue trabajos históricos, backup y datos reales.
5. AGENTS.md, docs/MATRIZ_MODULOS_CONTRATOS_TRASLADOS.md: una APK Mapa con Conductor integrado y responsables funcionales únicos.

Catálogo legible por máquina: docs/quicktrip-visual-core-v1.catalog.json. Sus 11 entradas son componentes lógicos INVENTARIADOS, no 11 componentes implementados ni autorizados para sustituir los existentes.

## 2. Auditoría por superficie

Web/PWA principal: web-pasajero/index.html conserva DOM/IDs del buscador, reserva, cuenta y mapa. web-pasajero/quicktrip-clean.js reubica nodos preexistentes (sin clonar listeners). styles.css declara tokens iniciales; quicktrip-clean.css es capa estable en main; en QA v90 se monta quicktrip-canonical-v86.css más capas de Samsung/GPS/ruta/Etapa B. El CSS QA v86 acumula declaraciones de alta especificidad y !important. No agregar otra capa correctiva: el saneamiento futuro será independiente, autorizado y probado.
Portada: tres servicios Tu Viaje, Tu Paquete y Tu Viaje Programado. Tema claro/oscuro actual en quicktrip-approved-v85-qa.js, con clase qt-v86-light; no introducir segunda persistencia o selector.
Mapa: premium-map.js y premium-route.js son renderers/motores existentes. El adaptador v90 muestra datos ilustrativos y, en Etapa B, un triángulo expresamente ficticio; no es GPS del chofer ni solicitud enviada al backend.
Android: MainActivity.java, TransferQuickPanel.java, HistoryBottomSheet.java y styles.xml contienen estilos nativos. Son candidatos a mapeo visual futuro exclusivamente: esta etapa NO modifica Android, Gradle, firma ni Conductor.

## 3. Contratos de montaje obligatorios

- QTServiceHome: #view-inicio, data-service now/delivery/schedule; jamás cuarto servicio ni restauración del flujo antiguo.
- QTHeader y QTTheme: cabecera qtc-header y logo qtc-logo, modificador existente qt-v86-light y botón [data-qt-component=theme-toggle]; la cabecera no puede invadir la barra Android.
- QTPlaces: mantener exactamente los nodos nativos #origin-picker y #destination-picker; búsquedas, GPS, departamento, paradas y persistencia permanecen bajo app.js.
- QTRouteMap: reutilizar .premium-route-canvas y su único mapa; estados verificables, sin recrear motor ni dibujar conductor como real con GPS ausente.
- QTDriverStatus: .qtc-driver debe diferenciar sin conductor, buscando, ETA verificada, GPS vencido y estado local de demo.
- QTTripMetrics: .qtc-submit-region exhibe precio/km/min reales solo cuando el motor entrega cálculo válido; QA ilustrativo siempre identificado.
- QTAccordion: .qtc-schedule, .qtc-contact e historial qtc-history conservan los listeners/estado de origen.
- QTBottomNav: .qtc-bottom-nav nunca tapa botones, textos editables ni tarjetas en 360/393/412 CSS px; medir centro táctil y scroll.
- QTRequestCTA: #urgent-send conserva handler/idempotencia original; permanece deshabilitado en preview v90.
- QTTripProgressQA: qt-b-sim/qt-b-flow son estados sintéticos SOLO LOCALES; ninguna aceptación/rechazo de un conductor real puede inferirse del dibujo.

Regla de integración futura: dueño de DOM/estilos separado del dueño de lógica; un componente visual no crea un endpoint, no llama directamente a RPC de reserva y no duplica métodos originales. Inventariar antes de reemplazar. Sin cambios funcionales en fase 1.

## 4. Tokens observados (no nuevas decisiones de marca)

Fuente: web-pasajero/styles.css.
--bg #071e25; --panel #09363f; --gold #e7cb85; --ivory #f8f5ed; --muted #b3c9c8; --green #74d9b0.

Estos valores son el inventario de la rama examinada, no una validación de contraste AA ni aprobación de identidad definitiva. El tema claro usa las clases y selectores existentes; falta normalizar tokens por tema con autorización específica.

## 5. Pruebas preparadas (sin crear workflow)

Archivo: web-pasajero/tests/quicktrip-visual-core-v1-qa.cjs.
- Contrato estático (sin dependencias externas): node web-pasajero/tests/quicktrip-visual-core-v1-qa.cjs
  Verifica SHA documental, tokens, IDs de DOM, marcadores de montaje, tres servicios, readonly/CSP, scripts QA y controles negativos deliberados. En un checkout Git, calcula git hash-object para motores protegidos y los compara con el manifiesto canónico.
- Capturas opcionales: NODE_PATH=/ruta/playwright-core CHROME_PATH=/ruta/chromium QA_DIR=/tmp/qt-visual-core-v1 node web-pasajero/tests/quicktrip-visual-core-v1-qa.cjs --browser
  Reutiliza Playwright Core y Chromium, crea un servidor SOLO localhost, bloquea todos los destinos remotos, desactiva Service Workers y captura home/trip a 360/393/412 CSS px y temas oscuro/claro. Comprueba que los nodos nativos siguen presentes, no hay desbordamiento horizontal, la petición está deshabilitada y la navegación no oculta acordeones. Reporta JSON y PNG fuera del repositorio.

Negativos: detectar token alterado, ID inexistente y servicio cuarto; en futuro ampliar con fixture sintético de cambio de CSS y control tapado. La prueba de Service Worker real YA pertenece a quicktrip-v90-pwa-cache-qa.cjs; no se reemplaza ni se confunde con un test visual que lo bloquea.
La referencia fotográfica privada aprobada todavía carece de hash SHA256 verificable. Las PNG de Chromium son evidencia de geometría y comportamiento, NO comparaciones pixel a pixel aprobadas ni fotografías Samsung.

## 6. Criterios de aceptación y límites

G0/G1 (esta etapa): manifiesto trazable, contratos verificables y prueba ejecutable sin mutar sistema operativo, backend o producción.
G2 futuro: ejecutar suites y guardar salida JSON/PNG con SHA exacto, repetir con fallo intencional, comparar con originales recuperados y registrar falsos positivos. Requiere autorización posterior para vincular al CI o cambiar CSS.
G3 futuro: prueba física Samsung, navegador/PWA instalada, permisos, modo claro/oscuro, scroll, teclado y botón Atrás; no sustituir con Chromium.
G4 futuro: cualquier consolidación de estilos o integración visual exige autorización adicional y pruebas por componente; no fusionar/publicar automáticamente.

Precauciones: no insertar datos privados, coordenadas, reservas, cuentas ni capturas de pasajeros en el repositorio público. No crear APK Cliente/Conductor. No copiar backend ni crear solicitudes reales. No modificar los workflows actuales, ni deshabilitar controles de CI. El workflow v90 contiene una lista exacta de archivos autorizados desde su propia base y no se modifica en esta fase; incorporar nuevos tests a ese workflow exigiría una etapa expresamente aprobada.

## 7. Entrega etapa 1

Resultado esperado: tres archivos nuevos (este informe, catálogo JSON, un test QA), todos bajo docs/ y web-pasajero/tests/. Verificar git diff desde SHA de partida y HEAD de main al terminar. No anunciar PASS browser hasta ejecución comprobada en Chromium ni aprobar fidelidad sin referencia original y Samsung real.
