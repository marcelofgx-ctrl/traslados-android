# Quick Trip QA — mapa más cercano, desplazable y con zoom

Base: seguimiento premium ya validado en QA, rama qa/quicktrip-premium-follow-20261027 @ 27f3b9cabe28b2529435de5415d561020c9e1ba2.

- La cartografía de Tu viaje sube de 238px a ~318px, sin eliminar los campos de origen/destino, recálculo y solicitar viaje.
- Ajuste de encuadre inicial con límite de zoom hasta 18, sin cortar una ruta real con origen y destino distintos.
- Controles + / −, centrar recorrido y Explorar / Listo.
- En el modo normal los gestos verticales se destinan al desplazamiento de la página; Explorar activa arrastre y pinch-zoom dentro del mapa. El control Listo devuelve el scroll normal.
- Si cambia el trayecto se vuelve a encuadrar; en seguimiento real el zoom manual no se reinicia con cada nuevo GPS. Solo se representa GPS autorizado y geometría vial verificada; no se inventan posiciones.
- Las teselas visibles se solicitan sin lazy loading, se mantienen derechos de atribución © OpenStreetMap y se elimina visualmente una tesela que falle.
- No hay cambios a Android, Supabase, Cloudflare, motor de reservas, búsqueda, ETA, precios o servicio de seguimiento.
- CSS y scripts con versión de caché QA separada; producción y main quedan sin cambios.

Prueba aceptable: screenshots móviles 390/412 px, botón + realmente acerca, arrastre cambia el centro, reset encuadra, vertical page scroll sigue disponible, sin desbordamiento horizontal, y pruebas de regresión de seguimiento, reserva, caducidad GPS y motores verdes.

No declarar terminado para producción sin prueba visual/física de Samsung. No publicar ni fusionar main.
