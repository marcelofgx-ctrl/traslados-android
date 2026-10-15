#!/usr/bin/env python3
"""Package the existing PR99 web into a network-isolated Android review build.
Only build output is changed. Production source and signing identity are retained.
"""
from pathlib import Path
import shutil, json, hashlib
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
html = html[:start] + "connect-src 'self'" + html[end:]
html = html.replace('<body>', '<body>\n<div class="preview-notice" role="status">VERSIÓN DE PRUEBA · PR #99 · No envía reservas</div>')
html = html.replace('</head>', '<link rel="stylesheet" href="./preview-isolation.css">\n</head>')
index.write_text(html)
(assets / "preview-isolation.css").write_text(".preview-notice{position:relative;z-index:10000;padding:8px 12px;background:#ebd1a2;color:#12343b;text-align:center;font:600 12px/1.4 sans-serif}")
js = assets / "app.js"
src = js.read_text()
assert 'if("serviceWorker" in navigator&&window.isSecureContext&&window.top===window.self)' in src
src = src.replace('if("serviceWorker" in navigator&&window.isSecureContext&&window.top===window.self)', 'if(false /* Isolated PR99 APK: no service workers */)')
js.write_text(src)
# No service-worker installation even if another component attempts it.
(assets / "sw.js").unlink(missing_ok=True)
gradle = app / "build.gradle"
g = gradle.read_text().replace("versionCode 204", "versionCode 205").replace("versionName '16.0-Premium'", "versionName '17.0-PR99-Prueba'")
g = g.replace("https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/", "https://appassets.androidplatform.net/traslados-android/web-pasajero/")
assert "versionCode 205" in g
gradle.write_text(g)
java = app / "src/main/java/uy/com/traslados/cliente/MainActivity.java"
j = java.read_text().replace('"marcelofgx-ctrl.github.io"', '"appassets.androidplatform.net"').replace("TrasladosClienteNative/16", "TrasladosClienteNative/17 PR99Preview")
needle = 'web.setWebViewClient(new WebViewClient() {'
assert needle in j
j = j.replace(needle, needle + """
            @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
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
                // Only public map images can leave this isolated APK. No backend, auth, or routing calls.
                if("https".equals(uri.getScheme()) && "tile.openstreetmap.org".equals(uri.getHost())
                    && "GET".equals(request.getMethod()) && uri.getPath().matches("/[0-9]+/[0-9]+/[0-9]+\\\\.png"))
                    return null;
                return blockedPreviewRequest();
            }
""")
j = j.replace("    private boolean openExternal(Uri uri) {", """
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
java.write_text(j)
assert "shouldInterceptRequest" in j and "tile.openstreetmap.org" in j
assert "zetaudvvutlouiqxopvg.supabase.co" not in html
assert "https://traslados-web.marcelof-gx.workers.dev" not in html
assert "appassets.androidplatform.net" in j and "appassets.androidplatform.net" in g
assert not (assets / "sw.js").exists()
manifest = {str(p.relative_to(assets)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(assets.rglob("*")) if p.is_file()}
(root / "preview-web-sha256.json").write_text(json.dumps(manifest, indent=2) + "\n")
print("PASS: PR99 assets packaged, isolated HTTPS origin, no production API, no external contact, code205, original applicationId.")
