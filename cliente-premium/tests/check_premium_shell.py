#!/usr/bin/env python3
"""Guardrails for the premium Cliente wrapper and the sole canonical web."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
app=(root/'app/src/main/java/uy/com/traslados/cliente/MainActivity.java').read_text()
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
gradle=(root/'app/build.gradle').read_text()
workflow=Path('.github/workflows/build-cliente-premium-v12.yml').read_text()
expected='https://traslados-web.marcelof-gx.workers.dev/'
assert f"'{expected}'" in gradle, 'Native Cliente must load official premium Worker'
assert "traslados-web.marcelof-gx.workers.dev" in app
assert "uy.com.traslados.cliente" in gradle and "uy.com.traslados.cliente" in app
assert "versionCode 200" in gradle
assert 'targetSdk 35' in gradle
assert 'minSdk 26' in gradle
assert 'android:usesCleartextTraffic="false"' in manifest
assert 'android:allowBackup="false"' in manifest
for item in [
  'setJavaScriptEnabled(true)',
  'setDomStorageEnabled(true)',
  'setGeolocationEnabled(true)',
  'ACCESS_FINE_LOCATION',
  'onGeolocationPermissionsShowPrompt',
  'onRequestPermissionsResult',
  'onReceivedSslError',
  'handler.cancel()',
  'setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW)',
  'setAllowFileAccess(false)',
  'setAllowUniversalAccessFromFileURLs(false)',
  'setAcceptThirdPartyCookies(web,false)',
  'onCreateWindow',
  'openExternal(next)',
  'OFFICIAL_WEB_URL',
  'Tus reservas necesitan Internet',
  'Volver a intentar',
]:
  assert item in app, f'Missing secure Android/WebView feature {item}'
assert not re.search(r'javascriptinterface|addJavascriptInterface|setAllowUniversalAccessFromFileURLs\(true\)',app,re.I)
assert not re.search(r'(?i)(sb_secret_|service_role|TRASLADOS_SIGNING_PASSWORD=)',app)
assert 'SIGNING_PASSWORD' in workflow and 'Verify signature' in workflow
assert "run-id: 38021100002" in workflow, 'must compare original signing certificate'
assert 'web-pasajero/downloads/traslados-cliente-premium-v12.apk' in workflow
print('PASS Cliente Premium: canonical URL, identical package, WebView TLS/GPS restrictions, signing, no secrets.')
