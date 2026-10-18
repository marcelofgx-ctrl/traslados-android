# PRESENCE-001 — publicación GPS comercial en Mapa (18/10/2026)

Incidencia: captura Samsung con jornada activa, GPS ±4 m y 45,1 km; al mismo tiempo la PWA informa que no puede recoger al pasajero. Una consulta de solo lectura a Supabase confirmó que el último latido comercial era del 17/10/2026 09:18 UYT y no había coordenadas comerciales frescas.

## Comportamiento y límites

- La pantalla Mapa ya tiene GPS y jornadas funcionando. No cambiar recogida, navegaciones, histórico ni escritura de puntos.
- La disponibilidad pública es una decisión del servidor: consentimiento local, jornada activa y libre, GPS fresco con precisión ≤45 m, horario/agenda y dispositivo autorizado. El semáforo verde solo representa consentimiento local; no prueba visibilidad pública.
- El envío de presencia antes ocultaba fallos HTTP y de red. Esta revisión registra **solo en preferencias privadas del teléfono** el resultado clasificado del último latido y las horas de intento, respuesta y confirmación de GPS. No guarda coordenadas, PIN, secretos, identificadores, solicitudes ni el cuerpo HTTP.
- Menú Mapa → Estado del sistema → Conectividad y jornada: consultar diagnóstico de publicación. Si aparece teléfono no autorizado, menú → Disponibilidad de recogida → Reautorizar este teléfono para recogidas. Requiere una segunda confirmación de consentimiento; nunca se reautoriza automáticamente.
- Si el usuario mantiene la opción apagada, no enviar GPS ni intentar reactivar sin su autorización.

## Contratos y QA

- El código clasificador puro vive en PickupPresenceDiagnostic.java; el test PickupPresenceDiagnosticTest.java comprueba estado GPS válido, precisión insuficiente, jornada y ocupación, no autorizado, HTTP y red sin filtrar datos.
- CI mantiene todas las pruebas existentes y assembleDebug integrado. La rama depende de Mapa R25.31 QA (#129), no se fusiona directamente en main ni crea APK Cliente o Conductor.
- En Samsung, instalar encima solo una APK de revisión con misma firma y versionCode mayor cuando exista. No desinstalar y no borrar históricos.
- Revisar último envío local, reautorizar si corresponde, esperar ~60 segundos y confirmar en Supabase sin consultar ni mostrar GPS privado. Después comprobar desde PWA disponibilidad y ETA, siempre sujetas a ruta verificada y agenda.
- No marcar solucionado hasta CI integrado, firma del APK QA, prueba Samsung real y comprobación del servidor. No publicar estable.

## APK de revisión candidata

Se prepara una compilación firmada R25.32-PRESENCE-QA, versionCode 84, con mismo applicationId y verificación contra el certificado R25.30 (también usado por R25.31). GitHub Actions genera un artefacto temporal; **no publica releases ni modifica la web**. La descarga no estará disponible hasta verificar SUCCESS, SHA exacto y checksum.

## Invitación al comienzo (siguiente revisión Android)

Al abrir Mapa, tras los permisos Android y la vinculación del conductor, si falta la autorización comercial se ofrece **una sola vez** el cuadro original «Autorizar GPS para recogidas». No se comparte nada por el mero hecho de abrir la app: se requiere pulsar **AUTORIZAR** y obtener respuesta del servidor. No mostrar mientras el conductor esté en viaje, el vehículo se mueva o haya una pantalla de permisos pendiente. Cancelar o desactivar expresamente evita avisos repetidos; el ajuste manual sigue en el menú. Los cambios están protegidos por `PickupConsentOnboardingPolicyTest` y la compilación nativa integrada; requieren nueva APK firmada para probarlos en Samsung.
