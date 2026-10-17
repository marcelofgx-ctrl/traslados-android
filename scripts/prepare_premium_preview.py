#!/usr/bin/env python3
"""Package the existing PR99 web into a network-isolated Android review build.
Only build output is changed. Production source and signing identity are retained.
"""
from pathlib import Path
import sys
from passenger_assets import sync_runtime, write_if_changed, manifest_json
operational = "--operational" in sys.argv[1:]
root = Path(__file__).resolve().parents[1]
app = root / "cliente-premium/app"
web = root / "web-pasajero"
assets = app / "src/main/assets/premium-preview"
# Derive every preview variant directly from the canonical web sources.
# No intermediate writes of originals, so identical runs preserve APK inputs.
html=(web / "index.html").read_text()
start=html.index("connect-src ")
end=html.index(";",start)
html=html[:start]+"connect-src 'self' https://zetaudvvutlouiqxopvg.supabase.co https://direcciones.ide.uy https://traslados-web.marcelof-gx.workers.dev"+html[end:]
if not operational:
    html=html.replace('<body>','<body>\n<div class="preview-notice" role="status">Vista de prueba v27 · No envía reservas</div>')
    html=html.replace('</head>','<meta name="traslados-preview" content="read-only">\n</head>')
else:
    html=html.replace('id="urgent-send" type="button" class="primary full booking-main-cta"><span class="booking-cta-label">ENVIAR SOLICITUD</span>',
                      'id="urgent-send" type="button" class="primary full booking-main-cta"><span class="booking-cta-label">SOLICITAR VIAJE</span>')
    assert 'SOLICITAR VIAJE' in html and 'name="traslados-preview"' not in html
html=html.replace('</head>','<link rel="stylesheet" href="./preview-isolation.css">\n</head>')

src=(web / "app.js").read_text()
needle='if("serviceWorker" in navigator&&window.isSecureContext&&window.top===window.self)'
assert needle in src, "Service-worker isolation must remain explicit"
src=src.replace(needle,'if(false /* Isolated PR99 APK: no service workers */)')

e=(web / "eta-engine.js").read_text()
needle='method:"POST",mode:"cors",cache:"no-store",signal:ctrl.signal,'
assert needle in e, "Unrecognized public ETA transport"
e=e.replace(needle,'method:"GET",mode:"cors",cache:"no-store",signal:ctrl.signal,',1)
e=e.replace(',\n        body:"{}"','',1)
start=e.index("  async function pickup(")
end=e.index("  root.TrasladosETA=",start)
e=e[:start]+'''  async function pickup({originLat,originLng,request=fetch}){
    const ctrl=new AbortController(),timer=setTimeout(()=>ctrl.abort(),18000);
    try{
      const url="https://appassets.androidplatform.net/traslados-android/web-pasajero/_public/pickup-eta?lat="+encodeURIComponent(originLat)+"&lng="+encodeURIComponent(originLng);
      const response=await request(url,{cache:"no-store",signal:ctrl.signal});
      return await response.json();
    }finally{clearTimeout(timer);}
  }
'''+e[end:]

css=".preview-notice{position:relative;z-index:10000;padding:5px 12px;background:#102a30;color:#dcc599;text-align:center;font:500 10px/1.4 sans-serif;border-bottom:1px solid #bda77533}"
asset_stats=sync_runtime(web, assets, {
    "index.html":html.encode("utf-8"),
    "app.js":src.encode("utf-8"),
    "eta-engine.js":e.encode("utf-8"),
    "preview-isolation.css":css.encode("utf-8"),
})
print("Passenger assets sync:",asset_stats)
gradle = app / "build.gradle"
g=gradle.read_text()
# Repeated local builds and switching isolated/operational must be idempotent.
for version in ("204","211","215"):
    g=g.replace("versionCode "+version,"versionCode 211")
for version in ("16.0-Premium","23.0-PR99-Prueba","27.0-Premium-Mosaico"):
    g=g.replace("versionName '"+version+"'","versionName '23.0-PR99-Prueba'")
g = g.replace("https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/", "https://appassets.androidplatform.net/traslados-android/web-pasajero/")
assert "versionCode 211" in g
if operational:
    g = g.replace("versionCode 211", "versionCode 215").replace("23.0-PR99-Prueba", "27.0-Premium-Mosaico")
g=g.replace("MOSAIC_PREVIEW_MODE', 'true'", "MOSAIC_PREVIEW_MODE', 'false'")
g=g.replace("MOSAIC_PREVIEW_OPERATIONAL', 'true'", "MOSAIC_PREVIEW_OPERATIONAL', 'false'")
g=g.replace("MOSAIC_PREVIEW_MODE', 'false'", "MOSAIC_PREVIEW_MODE', 'true'")
if operational:
    g=g.replace("MOSAIC_PREVIEW_OPERATIONAL', 'false'", "MOSAIC_PREVIEW_OPERATIONAL', 'true'")
write_if_changed(gradle, g.encode("utf-8"))
# The native WebView gateway is now real compiled Java, not a Python string
# replacement of MainActivity. Both isolated and operational use one audited
# resource path; Gradle mode flags select the server RPC allow-list.
java = app / "src/main/java/uy/com/traslados/cliente/MainActivity.java"
gateway = app / "src/main/java/uy/com/traslados/cliente/MosaicPreviewGateway.java"
j = java.read_text()
policy = gateway.read_text()
assert "MosaicPreviewGateway.intercept" in j
assert "BuildConfig.MOSAIC_PREVIEW_MODE" in j
assert 'customer_request_pickup_fixed_v1' in policy
assert "/api/public/route-estimate" in policy
assert 'tile.openstreetmap.org' in policy
assert "connect-src 'self' https://zetaudvvutlouiqxopvg.supabase.co https://direcciones.ide.uy https://traslados-web.marcelof-gx.workers.dev;" in html
assert "appassets.androidplatform.net" in j and "appassets.androidplatform.net" in g

assert not (assets / "sw.js").exists()
write_if_changed(root / "preview-web-sha256.json", manifest_json(assets))
print("PASS: operational passenger v27/code215; original identity, existing server authorization, no JS bridge." if operational else "PASS: PR99 read-only v23/code211; original identity; no production writes.")
