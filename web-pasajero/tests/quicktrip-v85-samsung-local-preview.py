#!/usr/bin/env python3
"""Quick Trip v85 — Samsung localhost-only READ-ONLY QA via adb reverse.
No Supabase access, remote scripts, external data fetch, local write APIs or deployment.
Run from a checked-out isolated QA branch.  Never expose to public network.
"""
from __future__ import annotations

from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit
import argparse
import sys

REPO = Path(__file__).resolve().parents[2]
PASSENGER = REPO / "web-pasajero"
HEAD = '''<meta name="traslados-preview" content="read-only">
<meta name="quicktrip-local-qa" content="samsung-v85-isolated-no-backend">'''
BANNER = '''<aside role="note" style="position:fixed;right:8px;top:8px;z-index:999999;
  background:#4b1020;color:white;border:2px solid #fff;font:700 11px/1.3 Arial;
  padding:5px;border-radius:5px;pointer-events:none;opacity:.95">
  QA LOCAL · SOLO LECTURA · SIN BACKEND
</aside>'''
# The page is entirely local and all external requests, including Supabase,
# street geocoding, map tiles and analytics, are blocked by the browser.
CSP = ("default-src 'self'; script-src 'self' 'unsafe-inline'; "
       "connect-src 'self'; img-src 'self' data: blob:; "
       "style-src 'self' 'unsafe-inline'; font-src 'self' data:; "
       "worker-src 'self'; manifest-src 'self'; "
       "object-src 'none'; frame-src 'none'; form-action 'none'; "
       "base-uri 'self'")

class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(REPO), **kwargs)

    def end_headers(self):
        self.send_header("Content-Security-Policy", CSP)
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("X-Robots-Tag", "noindex, nofollow")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Access-Control-Allow-Origin", "null")
        super().end_headers()

    def do_POST(self):
        self.send_error(405, "QA local no admite escrituras")

    def do_PUT(self):
        self.send_error(405, "QA local no admite escrituras")

    def do_DELETE(self):
        self.send_error(405, "QA local no admite escrituras")

    def do_GET(self):
        route = urlsplit(self.path).path
        if route in ("/web-pasajero", "/web-pasajero/", "/web-pasajero/index.html"):
            if not (PASSENGER / "index.html").is_file():
                self.send_error(404, "Falta web-pasajero/index.html")
                return
            html = (PASSENGER / "index.html").read_text(encoding="utf-8")
            if "<head>" not in html or "<body>" not in html:
                self.send_error(500, "HTML QA inesperado")
                return
            html = html.replace("<head>", "<head>\n" + HEAD, 1)
            html = html.replace("<body>", "<body>\n" + BANNER, 1)
            binary = html.encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(binary)))
            self.end_headers()
            self.wfile.write(binary)
            return
        super().do_GET()

def main():
    p = argparse.ArgumentParser(description="Read-only QA localhost for Samsung over adb reverse")
    p.add_argument("--port", type=int, default=8139)
    args = p.parse_args()
    if not 1024 <= args.port <= 65535:
        p.error("El puerto debe estar entre 1024 y 65535")
    if not (PASSENGER / "quicktrip-v85-resilience.js").exists():
        p.error("No se encuentra la rama corregida v85: falta quicktrip-v85-resilience.js")
    address = ("127.0.0.1", args.port)
    print("QUICK TRIP v85 — SOLO QA LOCAL / SIN SUPABASE / NO PRODUCCIÓN", flush=True)
    print(f"PC: http://localhost:{args.port}/web-pasajero/", flush=True)
    print(f"ADB: adb reverse tcp:{args.port} tcp:{args.port}", flush=True)
    print("Samsung Chrome (USB autorizado): abrí el mismo enlace localhost.", flush=True)
    print("No ingresar datos reales ni utilizar Solicitar viaje.", flush=True)
    with ThreadingHTTPServer(address, Handler) as httpd:
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            pass

if __name__ == "__main__":
    main()
