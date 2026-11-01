# Quick Trip P1.6 · animación discreta conductor · QA sin tocar lógica

Fecha 1/11/2026. Rama `qa/quicktrip-p16-conductor-motion-css-only-20261101`. Base exacta P1.4 confirmada físicamente en Samsung: `b47296a981dd93ef593b8bcf19aa3a4adae4f4a9`.

## Requisito funcional prioritario
En Samsung la P1.4 estable aprobada obtuvo **0,5 km** y **hasta 5 min** desde el conductor al origen, además de precio y ruta. Esos resultados son reales de la versión y deben conservarse intactos. La función `pickup-eta` en Supabase permite el origen estable `https://quicktrip-live-qa-only-20261027-marcelofgx-1927.vercel.app` pero no URLs aleatorias de previews. NO CAMBIAR el alias estable, origin allowlist ni función.

## Cambio estrictamente presentacional
La nueva `web-pasajero/quicktrip-p16-conductor-motion.css` se añade DESPUÉS del CSS P1.4 sin modificar los CSS existentes. Solo actúa sobre el bloque original `#pickup-presence` mientras el controlador publica `data-phase=checking|routing|waiting`:
- Un halo tenue alrededor del punto verde/ámbar original.
- Una línea luminosa finísima debajo del encabezado de la tarjeta, sin modificar texto ni dimensiones.
- Un borde apenas respirante en el cuadro exterior, donde `:has()` esté disponible.
- Cuando `data-phase=ready|blocked|issue|owned|origin|idle`, ninguna de estas animaciones corre.
- Respeta `prefers-reduced-motion: reduce`.

El CSS no crea ningún estado ni consulta. Los cambios adicionales son referenciar el archivo en el HTML y precacharlo en el SW existente, sin cambiar claves de caché ni la lógica del SW. Los scripts de usuario, App.js, rutas, ETA, mapa, tarifa y reservas permanecen byte por byte intactos.

**Límite**: si el controlador realiza un refresco de 15 s *sin cambiar el estado a waiting/routing/checking*, no se animará ese sondeo silencioso; presentar actividad falsa estaría en contra del requisito de respetar el funcionamiento aprobado. No añadimos eventos ni temporizadores.

## QA
GitHub Actions verifica un allowlist estricto de 7 archivos, sin cambios a módulos funcionales, corre todos los contratos P1.4 preexistentes y usa Chromium con respuestas HTTP exclusivamente simuladas para comprobar la animación activa, estado ready inerte, precio y ETA originales sin alteración y preferencia de movimiento reducido. Capturas sintéticas anexadas.

**No se cambió** main, producción, Supabase, API, Edge, Worker Cloudflare, Android ni APK. No reservas reales. No Vercel alias estable. No costos adicionales.

## Publicación
Este commit es solo candidato QA. La validación del conductor real desde cualquier preview con host no autorizado por Edge puede fallar por CORS; no atribuirlo a CSS. La versión P1.4 estable y su URL autorizada deben continuar funcionando. Requiere autorización separada antes de servir este CSS desde el alias estable y una última confirmación física en Samsung.
