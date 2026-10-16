#!/usr/bin/env python3
"""Package the existing PR99 web into a network-isolated Android review build.
Only build output is changed. Production source and signing identity are retained.
"""
from pathlib import Path
import shutil, json, hashlib, sys
operational = "--operational" in sys.argv[1:]
root = Path(__file__).resolve().parents[1]
app = root / "cliente-premium/app"
web = root / "web-pasajero"
assets = app / "src/main/assets/premium-preview"
if assets.exists():
    shutil.rmtree(assets)
assets.mkdir(parents=True)
allowed = {".html", ".js", ".css", ".svg", ".png", ".jpg", ".jpeg", ".webp", ".webmanifest", ".woff", ".woff2", ".json"}
for p in web.rglob("*"):
    rel = p.relative_to(web)
    if not p.is_file() or rel.parts[0] in {"downloads", "tests", "node_modules"} or p.suffix not in allowed:
        continue
    dest = assets / rel
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(p, dest)
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
index.write_text(html)
(assets / "preview-isolation.css").write_text(".preview-notice{position:relative;z-index:10000;padding:5px 12px;background:#102a30;color:#dcc599;text-align:center;font:500 10px/1.4 sans-serif;border-bottom:1px solid #bda77533}")
js = assets / "app.js"
src = js.read_text()
assert 'if("serviceWorker" in navigator&&window.isSecureContext&&window.top===window.self)' in src
src = src.replace('if("serviceWorker" in navigator&&window.isSecureContext&&window.top===window.self)', 'if(false /* Isolated PR99 APK: no service workers */)')
js.write_text(src)
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
eta.write_text(e)
# No service-worker installation even if another component attempts it.
(assets / "sw.js").unlink(missing_ok=True)
gradle = app / "build.gradle"
g = gradle.read_text().replace("versionCode 204", "versionCode 211").replace("versionName '16.0-Premium'", "versionName '23.0-PR99-Prueba'")
g = g.replace("https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/", "https://appassets.androidplatform.net/traslados-android/web-pasajero/")
assert "versionCode 211" in g
if operational:
    g = g.replace("versionCode 211", "versionCode 213").replace("23.0-PR99-Prueba", "25.0-Premium-Operativa")
gradle.write_text(g)
java = app / "src/main/java/uy/com/traslados/cliente/MainActivity.java"
j = java.read_text().replace('"marcelofgx-ctrl.github.io"', '"appassets.androidplatform.net"').replace("TrasladosClienteNative/16", "TrasladosClienteNative/23 PR99Preview")
needle = 'web.setWebViewClient(new WebViewClient() {'
assert needle in j
j = j.replace(needle, needle + """
            @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if(official(uri) && (OFFICIAL_PATH+"_public/pickup-eta").equals(uri.getPath()) && "GET".equals(request.getMethod())) {
                    try {
                        double lat=Double.parseDouble(uri.getQueryParameter("lat")),lng=Double.parseDouble(uri.getQueryParameter("lng"));
                        if(!Double.isFinite(lat)||!Double.isFinite(lng)||lat < -35.3||lat > -30||lng < -58.8||lng > -52.9) return blockedPreviewRequest();
                        org.json.JSONObject body=new org.json.JSONObject();body.put("originLat",lat);body.put("originLng",lng);
                        return previewPublicJson("https://zetaudvvutlouiqxopvg.supabase.co/functions/v1/pickup-eta",body.toString());
                    }catch(Exception invalid){return blockedPreviewRequest();}
                }
                if(official(uri) && "GET".equals(request.getMethod())) {
                    String relative = uri.getPath().length() <= OFFICIAL_PATH.length() ? "" : uri.getPath().substring(OFFICIAL_PATH.length());
                    if(relative.isEmpty()) relative = "index.html";
                    if(relative.contains("..") || relative.contains("\\\\") || relative.startsWith("/"))
                        return blockedPreviewRequest();
                    try {
                        String mime = java.net.URLConnection.guessContentTypeFromName(relative);
                        if(relative.endsWith(".js")) mime = "application/javascript";
                        if(relative.endsWith(".css")) mime = "text/css";
                        if(relative.endsWith(".svg")) mime = "image/svg+xml";
                        if(relative.endsWith(".webp")) mime = "image/webp";
                        if(mime == null) mime = "application/octet-stream";
                        java.util.Map<String,String> headers = new java.util.HashMap<>();
                        headers.put("Cache-Control","no-store");
                        headers.put("X-Content-Type-Options","nosniff");
                        return new android.webkit.WebResourceResponse(mime,"UTF-8",200,"OK",headers,
                            getAssets().open("premium-preview/" + relative));
                    } catch(java.io.IOException missing) { return blockedPreviewRequest(); }
                }
                // Audited STABLE public boolean only. All writes/auth/ETA remain blocked.
                if("https".equals(uri.getScheme()) && "zetaudvvutlouiqxopvg.supabase.co".equals(uri.getHost())
                    && "/rest/v1/rpc/public_driver_availability_v1".equals(uri.getPath())
                    && uri.getQuery() == null && ("GET".equals(request.getMethod()) || "OPTIONS".equals(request.getMethod())))
                    return null;
                // Public STABLE fare lookup only; arbitrary RPCs remain blocked.
                if("https".equals(uri.getScheme()) && "zetaudvvutlouiqxopvg.supabase.co".equals(uri.getHost())
                    && "/rest/v1/rpc/public_reference_quote_v1".equals(uri.getPath())
                    && ("GET".equals(request.getMethod()) || "OPTIONS".equals(request.getMethod()))
                    && uri.getQueryParameterNames().size()==1 && uri.getQueryParameter("p_distance_km")!=null) return null;
                // Public calculations/search only; no reservation/auth/contact APIs.
                if("https".equals(uri.getScheme()) && "traslados-web.marcelof-gx.workers.dev".equals(uri.getHost())
                    && "/api/public/route-estimate".equals(uri.getPath()) && "GET".equals(request.getMethod())) return previewPublicJson(uri.toString(),null);
                if("https".equals(uri.getScheme()) && "direcciones.ide.uy".equals(uri.getHost())
                    && java.util.Arrays.asList("/api/v1/geocode/candidates","/api/v1/geocode/direcUnica","/api/v1/geocode/reverse").contains(uri.getPath())
                    && "GET".equals(request.getMethod())) return previewPublicJson(uri.toString(),null);
                // Public raster map images.
                if("https".equals(uri.getScheme()) && "tile.openstreetmap.org".equals(uri.getHost())
                    && "GET".equals(request.getMethod()) && uri.getPath().matches("/[0-9]+/[0-9]+/[0-9]+\\\\.png"))
                    return null;
                return blockedPreviewRequest();
            }
""")
j = j.replace("    private boolean openExternal(Uri uri) {", """
    // Native public calculations use the approved first-party Origin, without account credentials.
    // No production CORS policy is changed; only these fixed contracts are reachable.
    private android.webkit.WebResourceResponse previewPublicJson(String target,String body) {
        java.net.HttpURLConnection connection=null;
        try {
            connection=(java.net.HttpURLConnection)new java.net.URL(target).openConnection();
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(5000);connection.setReadTimeout(14000);
            connection.setRequestProperty("Accept","application/json");
            if(body!=null){
                connection.setRequestMethod("POST");connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type","application/json");
                // This signed native shell is the official passenger client.
                // Guest ETA requires the same approved first-party Origin as its web.
                connection.setRequestProperty("Origin","https://marcelofgx-ctrl.github.io");
                connection.setRequestProperty("apikey","sb_publishable_HnbMZW2dKpm6mBq-y5qkaA_Jlfx4BB9");
                try(java.io.OutputStream out=connection.getOutputStream()){out.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            }
            int code=connection.getResponseCode();
            if(code<200||code>=600||(code>=300&&code<400))return blockedPreviewRequest();
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
            try(java.io.InputStream in=code>=400?connection.getErrorStream():connection.getInputStream()){
                if(in==null)return blockedPreviewRequest();
                byte[] buf=new byte[4096];int n;
                while((n=in.read(buf))!=-1){if(bytes.size()+n>524288)return blockedPreviewRequest();bytes.write(buf,0,n);}
            }
            java.util.Map<String,String> headers=new java.util.HashMap<>();
            headers.put("Access-Control-Allow-Origin","https://appassets.androidplatform.net");headers.put("Cache-Control","no-store");
            return new android.webkit.WebResourceResponse("application/json","UTF-8",code,"Public service",headers,new java.io.ByteArrayInputStream(bytes.toByteArray()));
        }catch(Exception unavailable){return blockedPreviewRequest();}
        finally{if(connection!=null)connection.disconnect();}
    }
    private android.webkit.WebResourceResponse blockedPreviewRequest() {
        return new android.webkit.WebResourceResponse("text/plain","UTF-8",503,"Preview isolated",
            java.util.Collections.singletonMap("Cache-Control","no-store"),
            new java.io.ByteArrayInputStream("Version de prueba: backend bloqueado".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    private boolean openExternal(Uri uri) {
        Toast.makeText(this,"Enlace externo desactivado en la versión de prueba",Toast.LENGTH_SHORT).show();
        return true;
    }
    private boolean unusedProductionExternal(Uri uri) {""")
if operational:
    # Only the passenger RPC contracts already used by this bundled first-party
    # app. Server session/token/PIN/ownership checks are unchanged. No tables,
    # driver commands, arbitrary RPCs, service keys or JS bridge are exposed.
    allowed = ["customer_request_pickup_v1", "customer_register", "customer_login",
        "customer_logout", "customer_claim_reservation", "customer_reservation_vehicle_v1",
        "customer_check_availability_v11_4", "customer_create_reservation_v12", "create_reservation",
        "customer_trip_chat_v1", "customer_trip_chat_send_v1", "cancel_reservation_by_token",
        "customer_cancel_reservation", "customer_quote_decision_v13", "customer_list_reservations_v15",
        "get_reservation_by_token_v4", "public_app_config_v1", "public_driver_availability_v1",
        "public_cached_route_preview_v1", "public_reference_quote_v1"]
    paths = ",".join('"/rest/v1/rpc/'+name+'"' for name in allowed)
    gate = '''                // Operational passenger contracts: authorization stays on the server.
                if("https".equals(uri.getScheme()) && "zetaudvvutlouiqxopvg.supabase.co".equals(uri.getHost())
                    && uri.getUserInfo()==null && uri.getPort()==-1 && uri.getQuery()==null
                    && !request.isForMainFrame() && ("POST".equals(request.getMethod()) || "OPTIONS".equals(request.getMethod()))
                    && java.util.Arrays.asList('''+paths+''').contains(uri.getPath())) return null;
'''
    j = j.replace('                // Public raster map images.', gate+'                // Public raster map images.')
    j = j.replace('private boolean openExternal(Uri uri) {\n        Toast.makeText', 'private boolean unusedPreviewExternal(Uri uri) {\n        Toast.makeText')
    j = j.replace('private boolean unusedProductionExternal(Uri uri)', 'private boolean openExternal(Uri uri)')
    j = j.replace('TrasladosClienteNative/23 PR99Preview', 'TrasladosClienteNative/25 Premium')
    assert 'Operational passenger contracts' in j and 'private boolean openExternal(Uri uri)' in j
java.write_text(j)
assert "shouldInterceptRequest" in j and "tile.openstreetmap.org" in j
assert "connect-src 'self' https://zetaudvvutlouiqxopvg.supabase.co https://direcciones.ide.uy https://traslados-web.marcelof-gx.workers.dev;" in html
assert "/api/public/route-estimate" in j
assert "appassets.androidplatform.net" in j and "appassets.androidplatform.net" in g
assert not (assets / "sw.js").exists()
manifest = {str(p.relative_to(assets)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(assets.rglob("*")) if p.is_file()}
(root / "preview-web-sha256.json").write_text(json.dumps(manifest, indent=2) + "\n")
print("PASS: operational passenger v25/code213; original identity, existing server authorization, no JS bridge." if operational else "PASS: PR99 read-only v23/code211; original identity; no production writes.")

