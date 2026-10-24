"use strict";
// QA-only reference classifier; NOT imported by web-pasajero or Android.
// No network, storage, UI, retry or booking mutation.
const BUSY_MESSAGE =
  "Conductor ocupado: terminá o cancelá el servicio anterior antes de aceptar otro.";

function isCapacityConflict(code, backendMessage) {
  if (code !== "23505") return false;
  const msg = typeof backendMessage === "string" ? backendMessage.toLowerCase() : "";
  return msg.includes('constraint "qt_one_active_service_uq"') ||
         msg.includes('index "qt_one_active_service_uq"');
}
function capacityMessageOrNull(code, message) {
  return isCapacityConflict(code, message) ? BUSY_MESSAGE : null;
}
module.exports = {BUSY_MESSAGE, isCapacityConflict, capacityMessageOrNull};
