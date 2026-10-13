#!/usr/bin/env python3
"""Create a short technical handoff from checked-in sources and GitHub run metadata."""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.error
import urllib.request

OWNER = "marcelofgx-ctrl"
REPO = OWNER + "/traslados-android"
BACKEND = OWNER + "/traslados-web"
SITE = "https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/"
DOWNLOADS = SITE + "downloads/"


def extract(pattern: str, data: str, label: str) -> str:
    found = re.search(pattern, data)
    if not found:
        raise ValueError("Version source missing: " + label)
    return found.group(1)


def versions(root: Path) -> dict:
    def android(path: str) -> tuple[str, str]:
        data = (root / path).read_text(encoding="utf-8")
        return (
            extract(r"versionName\s+['\"]([^'\"]+)", data, path),
            extract(r"versionCode\s+(\d+)", data, path),
        )
    pwa = extract(r"traslados-cliente-pwa-v(\d+)", (root / "web-pasajero/sw.js").read_text(encoding="utf-8"), "PWA")
    return {"pwa": pwa, "conductor": android("conductor/build.gradle"),
            "mapa": android("mapatrayectos/build.gradle"),
            "cliente": android("cliente-premium/app/build.gradle")}


def git(root: Path, *args: str) -> str:
    return subprocess.run(["git", *args], cwd=root, check=True,
                          capture_output=True, text=True).stdout.strip()


def github_json(path: str, offline: bool = False) -> dict | None:
    if offline:
        return None
    headers = {"Accept": "application/vnd.github+json", "User-Agent": "traslados-status/1"}
    token = os.getenv("GITHUB_TOKEN", "")
    if token:
        headers["Authorization"] = "Bearer " + token
    try:
        with urllib.request.urlopen(
            urllib.request.Request("https://api.github.com/" + path, headers=headers), timeout=8
        ) as reply:
            return json.load(reply)
    except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError, ValueError) as err:
        print("WARNING: GitHub status unavailable (" + type(err).__name__ + ")")
        return None


def recent(runs: list[dict], name: str) -> str:
    for item in runs:
        if item.get("name") != name or item.get("head_branch") != "main":
            continue
        result = item.get("conclusion") if item.get("status") == "completed" else item.get("status")
        label = {"success": "SUCCESS", "failure": "ERROR", "cancelled": "CANCELADO",
                 "in_progress": "EN CURSO", "queued": "EN COLA"}.get(result, "NO VERIFICADO")
        url = item.get("html_url", "")
        sha = str(item.get("head_sha", ""))[:9]
        if url.startswith("https://github.com/"):
            return "[" + label + "](" + url + ") · commit " + sha
        return label + " · commit " + sha
    return "Sin resultado verificable en esta consulta"


def render(root: Path, offline: bool = False, now: dt.datetime | None = None) -> str:
    v = versions(root)
    names = set(git(root, "ls-tree", "-r", "--name-only", "HEAD", "web-pasajero/downloads").splitlines())
    latest_android = github_json("repos/" + REPO + "/actions/runs?branch=main&per_page=70", offline) or {}
    latest_backend = github_json("repos/" + BACKEND + "/commits/main", offline) or {}
    backend_actions = github_json("repos/" + BACKEND + "/actions/runs?branch=main&per_page=15", offline) or {}
    runs = latest_android.get("workflow_runs", [])
    b_runs = backend_actions.get("workflow_runs", [])
    if not isinstance(runs, list): runs = []
    if not isinstance(b_runs, list): b_runs = []
    short_backend = latest_backend.get("sha", "")[:12]
    backend_link = ("[" + short_backend + "](https://github.com/" + BACKEND + "/commit/" + short_backend + ")") if short_backend else "No consultado"

    def apk_row(label: str, version: tuple[str, str], filename: str, workflow: str) -> str:
        present = ("web-pasajero/downloads/" + filename) in names
        link = "[Descargar APK](" + DOWNLOADS + filename + ")" if present else "**NO PUBLICADA EN REPOSITORIO**"
        return "| " + label + " | " + version[0] + " · code " + version[1] + " | " + link + " | " + recent(runs, workflow) + " |"

    conductor_name = "traslados-conductor-" + extract(r"(R\d+)$", v["conductor"][0], "conductor APK").lower() + ".apk"
    mapa_name = "mapa-trayectos-" + extract(r"(R\d+(?:\.\d+)*)$", v["mapa"][0], "mapa APK").lower() + ".apk"
    client_name = "traslados-cliente-premium-v" + extract(r"^(\d+)", v["cliente"][0], "cliente APK") + ".apk"
    lines = [
      "# ESTADO AUTOMÁTICO DE TRASLADOS",
      "",
      "**Versión detectada en las fuentes de GitHub** (regenerada únicamente cuando cambian versiones o resultados de ejecución).",
      "",
      "Este archivo es generado automáticamente. La versión procede del código y la columna de descarga refleja si el nombre de APK existe en Git; no certifica instalación ni funcionamiento en Samsung.",
      "",
      "| Producto | Versión fuente | Archivo verificable | Última ejecución encontrada |",
      "|---|---|---|---|",
      "| Pasajero PWA | v" + v["pwa"] + " | [Abrir web](" + SITE + ") | " + recent(runs, "pages build and deployment") + " |",
      apk_row("Cliente Premium", v["cliente"], client_name, "Build Cliente Premium v15 RELEASE"),
      apk_row("Conductor", v["conductor"], conductor_name, "Build Traslados Conductor RELEASE"),
      apk_row("Mapa Trayectos", v["mapa"], mapa_name, "Build Mapa Trayectos RELEASE"),
      "",
      "**PWA CI:** " + recent(runs, "Verify and package passenger PWA (GitHub Pages via branch)") + ".",
      "**Backend fuente:** " + backend_link + " · **último CI encontrado:** " + recent(b_runs, "Traslados Web — CI independiente") + ".",
      "",
      "### Interpretación y límites",
      "",
      "- Los resultados de Actions pueden ser de un commit anterior; comprobar su hash antes de afirmar que una versión exacta pasó las pruebas.",
      "- El despliegue de Cloudflare Worker y las migraciones de Supabase NO se infieren de un CI verde: confirmar cada publicación por separado.",
      "- No se actualizan aquí las decisiones, el estado QA Samsung ni los pendientes funcionales; consultar el [contexto de reanudación](REANUDAR_TRASLADOS.md).",
      "- Al iniciar otra sesión: leer este estado y después el contexto. Si el commit actual difiere del corte, volver a verificar GitHub.",
      "",
      "_Generación de documentación solamente: sin builds Android, sin claves, PIN, tokens ni coordenadas._",
      "",
    ]
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--offline", action="store_true")
    parser.add_argument("--output", type=Path, default=Path("docs/ESTADO_AUTOMATICO_TRASLADOS.md"))
    args = parser.parse_args()
    value = render(args.root, args.offline)
    path = args.root / args.output
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists() or path.read_text(encoding="utf-8") != value:
        path.write_text(value, encoding="utf-8")
        print("Estado generado: " + str(path))
    else:
        print("Estado técnico sin cambios")


if __name__ == "__main__":
    main()
