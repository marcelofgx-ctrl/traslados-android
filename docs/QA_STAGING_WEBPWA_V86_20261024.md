# Quick Trip v86 — Preparación de staging HTTPS exclusivamente QA

Origen exacto: `9c15f8465cd81e8a012f75101fe235031bc176e6` — rama fuente `qa/quicktrip-webpwa-canonica-v86-20261024`.

Rama de distribución aislada: `qa/quicktrip-staging-https-v86-20261024`. NO MERGE. NO PRODUCTION.

- Aplicación estática: `web-pasajero/` como raíz del proyecto de hospedaje.
- Build: `node qa-stage-build.cjs`; directorio servido: `dist/`. Copia byte-a-byte de archivos estáticos permitidos, únicamente raíz + icons/ + data/. Nunca publica APK ni el directorio downloads/.
- Sin secretos, variables del entorno, acceso a Supabase, ni cambios de motores.
- La app tiene CSP `connect-src 'none'`, `form-action 'none'`, banner visible de QA y Service Worker v86. No utilizar datos personales ni asumir creación de solicitudes reales.
- Configuración `vercel.json` permite usar **Preview Deployment** en un proyecto independiente del sitio Quick Trip productivo. No conectar esta rama a la rama de publicación productiva ni promocionar jamás automáticamente.
- No crear un proyecto Vercel asociado a la rama `main` de producción. Asegurar que cualquier URL sea exclusivamente de **Preview** y no corresponda al dominio productivo. La conexión de hosting es independiente y requiere cuenta/autorización correspondiente.
- Tras desplegar, antes de pasar link al Samsung, verificar código HTTP 200 y MIME de `/index.html`, `/manifest.webmanifest`, `/quicktrip-canonical-v86.css`, `/quicktrip-approved-v85-qa.js`, `/sw.js`, `/icons/icon-192.png`; cert TLS válido, banner QA visible, CSP, scope de manifest y SW.
- Control de versión: inspeccionar el contenido real del hosting y la huella de los assets contra GitHub. No confiar solo en el nombre v86.
- Probar instalación de PWA en Samsung Chrome, reapertura y offline; Atrás, origen/destino, programados, tema y navegación. Solo aprobar visual con fotografías de referencia.
- Android APK R25.38 firmada, artefacto 12549098318, se prueba aparte sin recompilar ni desinstalar.
- Prohibidas: publicar al dominio de producción, fusionar, borrar backups, compilar nuevas APK, consultar o modificar Supabase real `zetaudvvutlouiqxopvg`.

**Estado:** rama preparada con configuración de empaquetado. **No hay staging real ni enlace verificado hasta conectar hosting y comprobar el despliegue en vivo.**

## Auditoría visual complementaria (capturas reales del CI, NO Samsung físico)

Artifact `12578088180`, archivo `visual/02-tu-viaje-compacto-393.png`, inspeccionado directamente:

- El área del mapa aparece en gris con icono de imagen fallida. Sin origen/destino configurados no implica que el motor vial esté roto, pero **no demuestra un mapa correcto**. Revisar carga real de tiles/imagen y comportamiento después de calcular una ruta en un navegador Samsung, sin conectar Supabase.
- La navegación inferior flotante tapa parcialmente el contenido inmediatamente encima de «SOLICITAR VIAJE» en la captura completa. Es un **problema de composición y accesibilidad**, no superado por los asserts geométricos existentes.
- Captura `visual/01-circulos-v6-qa-393.png`: fondo de ciudad, logotipo Quick Trip y tres círculos presentes, pendiente cotejo 1:1 con referencia aprobada.
- La evidencia automatizada solo confirma existencia de elementos, no que el mapa se dibuje correctamente ni que la barra fija deje accesible todo el formulario.

**Bloqueos de aprobación visual final:** mapa/render físico y superposición barra/contenido; inspección en Samsung antes de promover. No fabricar mapas, kilómetros ni ETA. No alterar motores. Estas observaciones NO modifican archivos de la v86.
