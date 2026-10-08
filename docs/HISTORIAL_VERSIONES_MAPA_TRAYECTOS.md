# Mapa Trayectos — historial de versiones verificable
Estado a 08/10/2026. Mantener actualizado tras cada build y prueba de dispositivo.

| Versión | versionCode | Objetivo | Compilación | Prueba real |
|---|---:|---|---|---|
| R20 | 23 | Pulido premium petróleo/champagne, mapa y slider dual | Éxito, run 37719198104 | Usuario aprobó estética |
| R21 | 24 | Menú acciones rápidas y VCF; intento integración MP3 | Éxito, run 37775602458 | Menú abrupto, tarjeta WhatsApp fallaba |
| R21.1 | 25 | Menú compacto animado, chat por número y VCF separados | Éxito, run 37777016700 | Sin verificación completa |
| R21.2 | 26 | Tarjeta PNG con QR y vista previa | Éxito, run 37779090819 | Compartido necesita mejor UX |
| R21.3 | 27 | Logo C y foto incrustada en VCF | Éxito, run 37782391832 | WhatsApp no garantiza foto en burbuja |
| R21.4 | 28 | Abrir WhatsApp sin agendar; Cancelar último | Éxito, run 37785028964 | Usuario rechazó mensaje genérico; necesitaba tarjeta real |
| R21.5 | 29 | Tarjeta visual premium con logo C, chat a número no agendado, guardar pasajero con empresa opcional y enlaces confirmables | Éxito, run 37789496860 (firma APK v2; SHA256 9cef77c7dc1fbfe3b7a95f835fe8a2b1c0d146c76618e49b2b91c3ba9264b3b8) | PENDIENTE DE PRUEBA EN SAMSUNG |

## Notas importantes
- Todos los builds deben preservar el package uy.com.mapatrayectos y firma RELEASE previa para instalar encima.
- R21.5 está compilada y firmada. La apertura de WhatsApp, previsualización de imagen, inserción en Agenda y confirmación de URLs siguen sin prueba confirmada en Samsung.
- El código y un workflow exitoso NO verifican el envío real dentro de WhatsApp: queda sujeto a prueba en Samsung.
- Los recursos MP3 reales no fueron integrados: Pixabay devuelve 403 en GitHub CI; chimes de respaldo siguen.
- Las nuevas funciones de recordatorios y vaciar base con backup son ROADMAP, no R21.5.
