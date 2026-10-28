# Quick Trip · mapa completo de estilo transporte

Referencia visual: mapa oscuro de un servicio de transporte con ruta clara,
destino superior y hoja inferior de elección de viaje.

La pantalla se abre con «Ver mapa grande». Conserva los motores originales
de cálculo vial, kilómetros, ETA, precio de referencia UYU, identidad,
solicitud de reserva y privacidad. El overlay usa los valores ya verificados
desde premium-route.js y no los recalcula ni sustituye.

Sin automóvil inventado ni tarjeta de pago que Quick Trip no haya integrado.
Si no existe geometría real, solo muestra puntos O/D y bloquea el CTA hasta
obtener ruta y precio. «Continuar con mi viaje» vuelve al formulario de
datos personales o invoca la acción original de solicitar. No crea una API.

Regresiones obligatorias: pantalla móvil 390/412, mapa oscuro, ruta/price,
destino correcto, cerrar, validación de campos, zoom, arrastre, scroll,
reservas, seguimiento real y ausencia de cambios al backend comercial.
Trabajar en QA solamente; no publicar main ni APK.
