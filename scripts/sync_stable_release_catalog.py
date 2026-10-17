#!/usr/bin/env python3
"""Validate stable Android release files and advance the web catalog safely.

Only versioned RELEASE APKs with the original package ID, a higher versionCode,
and the same Android signing certificate may become public updates.
Preview/debug artifacts are never considered. Does not touch Supabase or user data.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DOWNLOADS = ROOT / "web-pasajero" / "downloads"
CATALOG = ROOT / "web-pasajero" / "releases.json"
PATTERNS = {
    "cliente": re.compile(r"traslados-cliente-premium-v([0-9]+)\.apk$"),
    "mapa": re.compile(r"mapa-trayectos-r([0-9]+)\.([0-9]+)\.apk$"),
    "conductor": re.compile(r"traslados-conductor-r([0-9]+)\.apk$"),
}
PACKAGES = {
    "cliente": "uy.com.traslados.cliente",
    "mapa": "uy.com.mapatrayectos",
    "conductor": "uy.com.traslados.conductor",
}
LABELS = {"cliente": "Cliente Premium", "mapa": "Mapa Trayectos", "conductor": "Conductor"}


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def android_command(name: str) -> str:
    found = shutil.which(name.upper()) or shutil.which(name)
    if found:
        return found
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if sdk:
        matches = sorted((Path(sdk) / "build-tools").glob(f"*/{name}"),
                         key=lambda p: tuple(int(n) for n in re.findall(r"\d+", p.parent.name)))
        if matches:
            return str(matches[-1])
    raise RuntimeError(f"No se encontró {name} en Android SDK")


def android_info(path: Path) -> tuple[str, int, str]:
    result = subprocess.check_output([android_command("aapt"), "dump", "badging", str(path)],
                                     text=True, stderr=subprocess.STDOUT)
    match = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", result, re.M)
    if not match:
        raise RuntimeError(f"No se pudo leer Android package/versionCode: {path.name}")
    return match[1], int(match[2]), match[3]


def signature(path: Path) -> str:
    result = subprocess.check_output([android_command("apksigner"), "verify", "--print-certs", str(path)],
                                     text=True, stderr=subprocess.STDOUT)
    match = re.search(r"Signer #1 certificate SHA-256 digest:\s*([a-fA-F0-9]{64})", result)
    if not match:
        raise RuntimeError(f"Sin certificado Android verificable: {path.name}; salida: {result[:650]!r}")
    return match[1].lower()


def candidates(kind: str):
    found = []
    for path in DOWNLOADS.glob("*.apk"):
        match = PATTERNS[kind].fullmatch(path.name)
        if match:
            found.append((tuple(map(int, match.groups())), path))
    if not found:
        raise RuntimeError(f"No hay APK estable para {kind}")
    return sorted(found, key=lambda pair: pair[0])[-1][1]


def verify_catalog_item(kind, item, android):
    expected = PACKAGES[kind]
    path = DOWNLOADS / item["file"]
    if path.name != item["file"] or not PATTERNS[kind].fullmatch(path.name):
        raise RuntimeError(f"Nombre no oficial para {kind}")
    if item["packageName"] != expected or item["versionCode"] < 1:
        raise RuntimeError(f"Identidad inválida en catálogo: {kind}")
    if not path.is_file() or path.stat().st_size < 10000 or not zipfile.is_zipfile(path):
        raise RuntimeError(f"APK ausente o inválida: {path.name}")
    if sha256(path) != item["sha256"]:
        raise RuntimeError(f"Checksum incorrecto para {path.name}")
    if android:
        package, code, version = android_info(path)
        if (package, code, version) != (expected, item["versionCode"], item["versionName"]):
            raise RuntimeError(f"Versión o identificador incorrectos para {path.name}")
        return signature(path)
    return None


def run(mode, android):
    catalog = json.loads(CATALOG.read_text())
    if catalog.get("schemaVersion") != 1 or catalog.get("channel") != "stable":
        raise RuntimeError("Catálogo estable inválido")
    updates = []
    for kind in PACKAGES:
        item = catalog["releases"][kind]
        old_signer = verify_catalog_item(kind, item, android)
        newest = candidates(kind)
        if newest.name == item["file"]:
            print(f"OK {kind}: {item['versionName']} / code {item['versionCode']}")
            continue
        # A new stable file is never accepted without Android package and
        # signer verification, even when the checksum looks correct.
        if not android:
            raise RuntimeError(f"Se requiere Android SDK para promover {newest.name}")
        package, code, version = android_info(newest)
        new_signer = signature(newest)
        if package != PACKAGES[kind] or new_signer != old_signer:
            raise RuntimeError(f"Identidad o firma diferente en {newest.name}: abortar publicación")
        if code <= item["versionCode"]:
            raise RuntimeError(f"versionCode debe aumentar en {newest.name}")
        if mode == "check":
            raise RuntimeError(f"Catálogo desactualizado: {kind} {item['file']} => {newest.name}")
        match = PATTERNS[kind].fullmatch(newest.name)
        label = ("v" if kind == "cliente" else "R") + ".".join(match.groups())
        catalog["releases"][kind] = {
            "name": LABELS[kind], "packageName": package,
            "versionName": version, "versionCode": code,
            "displayVersion": label,
            "file": newest.name, "sha256": sha256(newest),
        }
        updates.append(kind)
        print(f"PROMOVER {kind}: {version} / code {code} / firma existente")
    if updates:
        CATALOG.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n")
    return updates


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", choices=["check", "sync"], default="check")
    parser.add_argument("--android-tools", action="store_true")
    opts = parser.parse_args()
    try:
        updates = run(opts.mode, opts.android_tools)
        print("OK stable catalog" + (": " + ",".join(updates) if updates else " sin cambios"))
    except (OSError, KeyError, TypeError, ValueError, RuntimeError, subprocess.CalledProcessError) as error:
        print(f"ERROR validación de releases: {error}", file=sys.stderr)
        sys.exit(1)
