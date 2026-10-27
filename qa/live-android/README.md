Quick Trip Live GPS Android QA

El buildType qaLive de Mapa QT agrega una actividad y servicio QA. No modifica TrackingService ni código de producción. Solo lee broadcasts de Mapa QT y remite a un Worker QA HTTPS con permiso explícito para una reserva QA ACEPTADA. Token introducido manualmente en pantalla y retenido solo en memoria. No publica GPS sin jornada/viaje activos y frescos. Revoca al detener o terminar el viaje. Si el proceso muere, el Worker oculta las coordenadas después de 45 segundos.

NO está desplegado el Worker D1 QA. NO se ha validado todavía la conexión real Samsung↔pasajero. No publicar ni fusionar este buildType a main. 
