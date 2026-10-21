#!/usr/bin/env bash
# READ ONLY. Never uninstalls apps, resets storage, or changes device settings.
# Usage: bash scripts/android_qa_install_preflight.sh file.apk expected.package.id
set -euo pipefail
if [[ "$#" -lt 2 || ! -f "$1" ]]; then
  echo "Uso: bash $0 archivo.apk paquete.esperado" >&2
  exit 2
fi
APK="$1"
EXPECTED="$2"
SDK="$(printenv ANDROID_SDK_ROOT || true)"
if [[ -z "$SDK" ]]; then SDK="$(printenv ANDROID_HOME || true)"; fi
if [[ -z "$SDK" ]]; then SDK="/usr/local/lib/android/sdk"; fi
AAPT="$(command -v aapt || true)"
SIGNER="$(command -v apksigner || true)"
if [[ -z "$AAPT" ]]; then
  AAPT="$(find "$SDK/build-tools" -maxdepth 2 -type f -name aapt 2>/dev/null | sort -V | tail -1)"
fi
if [[ -z "$SIGNER" ]]; then
  SIGNER="$(find "$SDK/build-tools" -maxdepth 2 -type f -name apksigner 2>/dev/null | sort -V | tail -1)"
fi
if [[ ! -x "$AAPT" || ! -x "$SIGNER" ]]; then
  echo "Faltan aapt/apksigner. Cancelando sin instalar nada." >&2
  exit 3
fi

BADGING="$("$AAPT" dump badging "$APK")"
PACKAGE="$(printf '%s\n' "$BADGING" | awk -F"'" '/^package: name=/{print $2; exit}')"
LABEL="$(printf '%s\n' "$BADGING" | awk -F"'" '/^application-label:/{print $2; exit}')"
if [[ "$PACKAGE" != "$EXPECTED" ]]; then
  echo "ERROR: paquete distinto. Esperado: $EXPECTED; Detectado: $PACKAGE" >&2
  exit 4
fi
STABLE_MAPA=false
if [[ "$PACKAGE" == "uy.com.mapatrayectos.qa" ]]; then STABLE_MAPA=true; fi
if [[ "$STABLE_MAPA" != true && ! "$PACKAGE" =~ ^uy\.com\.(mapatrayectos|traslados\.(conductor|cliente))\.qa\.r[0-9]+$ ]]; then
  echo "ERROR: el APK no tiene identificador exclusivo para QA." >&2
  exit 5
fi
if [[ "$LABEL" != *QA* ]]; then
  echo "ERROR: falta etiqueta QA visible: $LABEL" >&2
  exit 6
fi

CERTS="$("$SIGNER" verify --verbose --print-certs "$APK")"
if ! printf '%s\n' "$CERTS" | grep -Eq 'Verified using v2 scheme.*true|Verified using v3 scheme.*true'; then
  echo "ERROR: APK sin firma v2/v3 válida para Android." >&2
  exit 7
fi
FINGERPRINT="$(printf '%s\n' "$CERTS" | sed -n 's/.*certificate SHA-256 digest: //p' | head -1)"
if [[ -z "$FINGERPRINT" ]]; then
  echo "ERROR: firma Android no verificable." >&2
  exit 7
fi
if [[ "$STABLE_MAPA" == true ]]; then
  EXPECTED_CERT="d91f4b9a37f4c77653fdf18fe792e7011a046dd2c09fded1e13fd29d4267269d"
  if [[ "${FINGERPRINT,,}" != "$EXPECTED_CERT" ]]; then
    echo "ERROR: Mapa QA estable no tiene el certificado persistente autorizado." >&2
    exit 9
  fi
fi
echo "OK: Firma Android verificada."
echo "OK: Paquete QA exclusivo: $PACKAGE"
echo "OK: Etiqueta: $LABEL"
echo "SHA-256 certificado: $FINGERPRINT"
echo "SHA-256 APK: $(sha256sum "$APK" | awk '{print $1}')"

# With ADB, check collisions without deleting apps or user data.
if command -v adb >/dev/null 2>&1 && [[ "$(adb get-state 2>/dev/null || true)" == "device" ]]; then
  if [[ -n "$(adb shell pm path "$PACKAGE" 2>/dev/null | tr -d '\r')" ]]; then
    if [[ "$STABLE_MAPA" == true ]]; then
      echo "QA estable presente. Instalación actualizable con adb install -r si versionCode aumenta."
    else
      echo "ERROR: ya existe este paquete QA efímero." >&2
      exit 8
    fi
  else
    echo "OK: primera instalación de este paquete QA."
  fi
  echo "Instalaciones QA antiguas (no se borran):"
  adb shell pm list packages 2>/dev/null | tr -d '\r' | \
    grep -E '^package:uy\.com\.(mapatrayectos|traslados\.(conductor|cliente))\.qa\.r[0-9]+$' || true
fi
if [[ "$STABLE_MAPA" == true ]]; then
  echo "LISTO: paquete y firma estables; futuras builds con versionCode mayor lo actualizan."
else
  echo "LISTO: QA aislada sin reemplazar aplicaciones originales."
fi
