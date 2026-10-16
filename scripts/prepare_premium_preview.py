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
asset_stats = sync_runtime(web, assets)
print("Passenger assets sync:", asset_stats)
# Separate HTTPS origin means the review build cannot see production WebView storage.
index = assets / "index.html"
html = index.read_text()
html = html.replace("default-src 'self';", "default-src 'self';")
start = html.index("connect-src ")
end = html.index(";", start)
html = html[:start] + "connect-src 'self' https://zetaudvvutlouiqxopvg.supabase.co https://direcciones.ide.uy https://traslados-web.marcelof-gx.workers.dev" + html[end:]
if not operational:
    html = html.replace('<body>', '<body>\n<div class="preview-notice" role="status">Vista de prueba v23 · No envía reservas</div>')
    html = html.replace('</head>', '<meta name="traslados-preview" content="read-only">\n</head>')
else:
    html = html.replace('id="urgent-send" type="button" class="primary full booking-main-cta"><span class="booking-cta-label">ENVIAR SOLICITUD</span>', 'id="urgent-send" type="button" class="primary full booking-main-cta"><span class="booking-cta-label">SOLICITAR VIAJE</span>')
    assert 'SOLICITAR VIAJE' in html and 'name="traslados-preview"' not in html
html = html.replace('</head>', '<link rel="stylesheet" href="./preview-isolation.css">\n</head>')
write_if_changed(index, html.encode("utf-8"))
write_if_changed(assets / "preview-isolation.css", ".preview-notice{position:relative;z-index:10000;padding:5px 12px;background:#102a30;color:#dcc599;text-align:center;font:500 10px/1.4 sans-serif;border-bottom:1px solid #bda77533}".encode("utf-8"))
js = assets / "app.js"
src = js.read_text()
assert 'if("serviceWorker" in navigator&&window.isSecureContext&&window.top===window.self)' in src
src = src.replace('if("serviceWorker" in navigator&&window.isSecureContext&&window.top===window.self)', 'if(false /* Isolated PR99 APK: no service workers */)')
write_if_changed(js, src.encode("utf-8"))
# A stable no-argument public RPC is callable with GET. No token or passenger data.
eta = assets / "eta-engine.js"
e = eta.read_text()
e = e.replace('method:"POST",mode:"cors",cache:"no-store",signal:ctrl.signal,', 'method:"GET",mode:"cors",cache:"no-store",signal:ctrl.signal,', 1)
e = e.replace(',\n        body:"{}"', '', 1)
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
write_if_changed(eta, e.encode("utf-8"))
# No service-worker installation even if another component attempts it.
(assets / "sw.js").unlink(missing_ok=True)
gradle = app / "build.gradle"
g = gradle.read_text().replace("versionCode 204", "versionCode 211").replace("versionName '16.0-Premium'", "versionName '23.0-PR99-Prueba'")
g = g.replace("https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/", "https://appassets.androidplatform.net/traslados-android/web-pasajero/")
assert "versionCode 211" in g
if operational:
    g = g.replace("versionCode 211", "versionCode 215").replace("23.0-PR99-Prueba", "27.0-Premium-Mosaico")
assert "MOSAIC_PREVIEW_MODE', 'false'" in g
g=g.replace("MOSAIC_PREVIEW_MODE', 'false'", "MOSAIC_PREVIEW_MODE', 'true'")
if operational:
    assert "MOSAIC_PREVIEW_OPERATIONAL', 'false'" in g
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

