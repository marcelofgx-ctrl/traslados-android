# Quick Trip V6 — Primera implementación real de interfaz (19/10/2026)

## Identidad aprobada como dirección
- Marca mostrada: **Quick Trip**. `go` es un **acento gráfico superíndice** sin formar parte de la denominación principal.
- Referencias: mockups de cuatro pantallas, con variante **petróleo profundo + champagne** y variante **marfil claro**. Se trabaja con una sola estructura para ambos temas.
- La composición Quick Trip está escrita en `web-pasajero/pruebas/visual-v6/quicktrip-v6.js` y los tokens únicos de estilo están en `quicktrip-v6.css`. No incorpora CSS V1–V5 ni cambia ningún archivo público.

## Implementado y verificable en laboratorio
- Header compacto, logotipo, cuatro pasos con estado activo y completado, botón de volver, resumen de ruta, mapa ilustrativo, precio destacado, paradas plegables, ETA de ejemplo, CTA y navegación inferior de demostración.
- **Botón ☀ / ☾ en el encabezado**: actualiza TODOS los tokens visuales sin reestructurar el DOM, cambia `meta[name=theme-color]` para la barra Android, actualiza accesibilidad y persiste preferencia en `localStorage['quicktrip-v6-theme']` (única persistencia de este laboratorio).
- Horarios en dos columnas: ahora, 10, 15, 20, 30, 45, 60 minutos u otro momento. Navegación y selección en el laboratorio, sin acceso a cuentas.
- Precio/km/min/origen/destino y recogida se siguen renderizando desde el mismo modelo V6 a pasos 1 y 4, con las guardas previas de validez de la ruta. Los valores son **ficticios de ejemplo** en este laboratorio, no pueden usarse para cotizar un viaje.
- Modo oscuro inicial; modo claro idéntico en distribución. Disponibles en el mismo URL.
- Botón final sigue deshabilitado: **SIN ENVÍO DE RESERVAS**. Sin red, GPS, Supabase, métodos de pago, cuenta personal ni base de datos.

## Criterio de fidelidad y limitaciones
Las imágenes de referencia son **renders ideales**, no una maqueta de componentes exportables. Este primer código busca reproducir proporciones, paleta, jerarquía y funciones de interfaz; **no puede afirmarse aún que cada píxel sea idéntico**. No se aprueba el diseño a producción sin capturas físicas en Samsung y comparación contra las dos referencias. El mapa es SVG ilustrativo; en G4 se reemplazará por el mapa vial real del motor existente. El logo en SVG es una aproximación escalable del concepto gráfico aprobado; requiere validación comparativa y, si es necesario, sustitución por el asset definitivo para mayor fidelidad.

## G4 — Integración con la PWA funcional (siguiente rama, no ejecutado aquí)
- Conectar el estado de V6 con el motor probado de geocodificación, rutas, paradas, precio parametrizable y ETA por medio de adaptadores de sólo lectura, sin modificar cálculos.
- Mantener salvaguardas de ruta actual y origen actual para impedir valores obsoletos.
- Mapas: los dos temas deben poder activar estilos propios sin volver a calcular la ruta sólo por cambiar el color.
- Conectar cuenta, horarios, mis viajes, mensajes y reserva desde el backend existente con controles de envío, idempotencia y permisos.
- QA en Samsung: anchos 360/393/412 px, tema persistente tras recarga/reapertura, scroll sin oclusión del CTA, aplicación instalada / navegador, Back Android y regreso, busca aeropuerto/WTC, 0/1/2 paradas, cotizaciones actualizadas, ETA.
- Promoción a web oficial sólo después de evaluación funcional y visual. No APK Cliente independiente; APK Mapa Trayectos no afectada.

## Entregables de este PR
Laboratorio visual V6 actualizado y test de alcance + tests `static-v6.cjs` / `view-model-v6.cjs`. Compilar esta maqueta con Node no compila Android. Se despliega como carpeta aislada de GitHub Pages. No alterar `web-pasajero/` producción, Map-First V5.3 ni Supabase.