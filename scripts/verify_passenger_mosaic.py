#!/usr/bin/env python3
"""Read-only contract audit of the single canonical passenger web and APK.

This is the fast mosaico gate: it catches missing static dependencies and
divergent SW/HTML contracts before launching Gradle or the Android emulator.
"""
from __future__ import annotations
from pathlib import Path
import hashlib
import json
import re

root = Path(__file__).resolve().parents[1]
web = root/"web-pasajero"
html = (web/"index.html").read_text(encoding="utf-8")
sw = (web/"sw.js").read_text(encoding="utf-8")
app = (web/"app.js").read_text(encoding="utf-8")
booking = (web/"booking-contracts.js").read_text(encoding="utf-8")
route = (web/"route-engine.js").read_text(encoding="utf-8")
eta = (web/"eta-engine.js").read_text(encoding="utf-8")

scripts = re.findall(r'<script\s+src="\./([^"?]+\.js)(?:\?[^"]*)?"\s+defer\s*>', html)
if len(scripts) < 12 or len(scripts) != len(set(scripts)):
    raise ValueError("Duplicate/missing passenger mosaic JavaScript components")
if scripts[-1] != "app.js":
    raise ValueError("Passenger coordinator app.js must initialize last")
for name in scripts:
    if not (web/name).is_file():
        raise ValueError("Browser component missing: "+name)
match = re.search(r'const files=(\[[^;]+\]);', sw)
if not match:
    raise ValueError("Cannot audit service-worker offline dependencies")
cached = set(json.loads(match.group(1)))
for name in scripts:
    if "./"+name not in cached:
        raise ValueError("PWA offline asset missing: "+name)
for css in re.findall(r'<link[^>]+href="\./([^"?]+\.css)(?:\?[^"]*)?"',html):
    if not (web/css).is_file() or "./"+css not in cached:
        raise ValueError("PWA style not included in offline cache: "+css)
for path in cached:
    relative = path.removeprefix("./")
    if relative and not (web/relative).exists() and relative != ".":
        raise ValueError("Stale/missing SW cache asset: "+path)
for required in ("customer_request_pickup_fixed_v1","customer_quote_decision_v13",
                 "public_reference_quote_v1","customer_check_availability_v11_4"):
    if not any(required in part for part in (app,booking,route,eta)):
        raise ValueError("Required passenger backend contract lost: "+required)
if "No se envían reservas offline" not in (web/"offline.html").read_text():
    raise ValueError("Offline bookings must remain disabled")
if any(key in app for key in ("sb_secret_","service_role")):
    raise ValueError("Server credentials accidentally exposed to clients")
manifest = {
    "scriptCount": len(scripts),
    "scripts": scripts,
    "webCoordinatorBytes": len((web/"app.js").read_bytes()),
    "webRuntimeBytes": sum((web/name).stat().st_size for name in scripts),
    "offlineFileCount": len(cached),
    "dependencySha256": hashlib.sha256(
        "".join(name+hashlib.sha256((web/name).read_bytes()).hexdigest()
                for name in scripts).encode("ascii")
    ).hexdigest(),
}
print("PASS: passenger mosaic module order, offline parity, backend contracts and secrets")
print(json.dumps(manifest, ensure_ascii=False, indent=2))
