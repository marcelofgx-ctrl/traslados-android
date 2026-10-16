"""Static regression for Conductor/Mapa passenger chat entrypoints.
No network, no database writes, no messages sent."""
from pathlib import Path
root=Path(__file__).resolve().parents[2]
main=(root/"feature/driver/src/main/java/uy/com/traslados/conductor/MainActivity.java").read_text()
monitor=(root/"feature/driver/src/main/java/uy/com/traslados/conductor/ReservationMonitorService.java").read_text()
quick=(root/"mapatrayectos/src/main/java/uy/com/mapatrayectos/TransferQuickPanel.java").read_text()
api=(root/"feature/driver/src/main/java/uy/com/traslados/conductor/Api.java").read_text()
assert 'openRequestedPassengerChatIfReady()' in main
assert 'pendingChatReservationId=intent.getBooleanExtra("open_chat",false)' in main
assert '?driverBubbleReservationId:"";' in main
assert 'intent.getBooleanExtra("open_chat",false)' in main
assert 'open.putExtra("open_chat",true)' in monitor
assert 'i.putExtra("open_chat",true)' in quick
assert 'openConductorChat(idFinal)' in quick
assert 'CONVERSAR CON EL PASAJERO' in main
assert 'CONVERSACIÓN DEL TRASLADO' in quick
assert 'driver_trip_chat_v1' in api and 'driver_trip_chat_send_v1' in api
assert 'showReservationChat(row)' in main
assert 'showReservationChat(r)' in main
assert 'if(isDriving())' in quick
assert 'draft.setText(template)' in main
assert 'choice.setOnClickListener' in main
assert 'Api.sendTripChat(pin,id,body,pendingId[0])' in main
assert main.index('draft.setText(template)') < main.index('Api.sendTripChat(pin,id,body,pendingId[0])')
assert 'if(archived)send.setVisibility(View.GONE)' in main
assert 'com.equalsIgnoreCase("null")' in main
assert 'installedConductorVersion()' in main
assert 'WHATSAPP · ESTOY EN CAMINO' in quick
assert 'handler.postDelayed(activeChatRefresh[0],8000L)' in main
assert 'dialog.setOnDismissListener(v->handler.removeCallbacks(activeChatRefresh[0]))' in main
assert 'if(signature.equals(list.getTag()))return;' in main
assert '!list.isAttachedToWindow()||!requestPin.equals(pin)' in main
assert 'if(nearEnd)scroll.fullScroll(View.FOCUS_DOWN)' in main
print("PASS native chat from bubble/alert/notification, editable quick drafts, driver RPC, archive and driving guard")
