#!/usr/bin/env python3
"""Create a short technical handoff from checked-in sources and GitHub run metadata."""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
from zoneinfo import ZoneInfo
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
    pwa = extract(r"traslados-(?:cliente-)?pwa-v(\d+)", (root / "web-pasajero/sw.js").read_text(encoding="utf-8"), "PWA")
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


def time_uy(iso: str | None) -> str:
    """GitHub UTC event timestamp, presented in Montevideo's civil time."""
    if not iso:
        return "fecha/hora no informada"
    try:
        stamp = dt.datetime.fromisoformat(str(iso).replace("Z", "+00:00"))
        if stamp.tzinfo is None:
            return "fecha/hora sin zona verificable"
        return stamp.astimezone(ZoneInfo("America/Montevideo")).strftime("%d/%m/%Y %H:%M UYT")
    except (TypeError, ValueError):
        return "fecha/hora inválida"


def build_time(run: dict) -> str:
    # GitHub workflow updated_at is the final update, not compilation-start time.
    # Never claim this is an exact timestamp from apksigner or Gradle.
    raw = run.get("updated_at") if run.get("status") == "completed" else (
        run.get("run_started_at") or run.get("created_at"))
    return time_uy(raw)


def latest_app_build(runs: list[dict]) -> dict | None:
    names = {
        "Verify and package passenger PWA (GitHub Pages via branch)",
        "Quick Trip V7 - APKs de testing",
        "Build Mapa Trayectos RELEASE",
    }
    finished = [r for r in runs if r.get("head_branch") == "main"
                and r.get("name") in names and r.get("status") == "completed"
                and r.get("conclusion") == "success" and r.get("updated_at")]
    return max(finished, key=lambda r: r["updated_at"], default=None)


def latest_code_change(offline: bool) -> dict | None:
    """Ignore automatic documentation commits by querying relevant source paths."""
    tracks = [
        (REPO, "web-pasajero", "Web pasajero"),
        (REPO, "conductor", "Conductor"),
        (REPO, "mapatrayectos", "Mapa Trayectos"),
        (REPO, "cliente-premium", "Cliente Premium"),
        (REPO, "core-reservations", "Código Android compartido"),
        (BACKEND, "src", "Backend Web/Worker"),
        (BACKEND, "supabase/migrations", "Supabase / migraciones"),
    ]
    if offline:
        return None
    def fetch_track(spec: tuple[str, str, str]) -> dict | None:
        repository, path, label = spec
        response = github_json("repos/" + repository + "/commits?sha=main&path=" + path + "&per_page=1")
        if not isinstance(response, list) or not response:
            return None
        commit = response[0]
        date = ((commit.get("commit") or {}).get("committer") or {}).get("date")
        sha = commit.get("sha", "")
        if not isinstance(date, str) or not isinstance(sha, str) or len(sha) < 10:
            return None
        subject = str(((commit.get("commit") or {}).get("message") or "").splitlines()[0])[:90]
        return {"timestamp": date, "sha": sha, "repository": repository,
                "area": label, "subject": subject.replace("|", "/")}
    with ThreadPoolExecutor(max_workers=7) as pool:
        results = list(pool.map(fetch_track, tracks))
    valid = [item for item in results if item is not None]
    return max(valid, key=lambda item: item["timestamp"], default=None)


def recent(runs: list[dict], name: str) -> str:
    for item in runs:
        if item.get("name") != name or item.get("head_branch") != "main":
            continue
        result = item.get("conclusion") if item.get("status") == "completed" else item.get("status")
        label = {"success": "SUCCESS", "failure": "ERROR", "cancelled": "CANCELADO",
                 "in_progress": "EN CURSO", "queued": "EN COLA"}.get(result, "NO VERIFICADO")
        url = item.get("html_url", "")
        sha = str(item.get("head_sha", ""))[:9]
        observed = build_time(item)
        label = label + " · " + observed
        if url.startswith("https://github.com/"):
            return "[" + label + "](" + url + ") · commit " + sha
        return label + " · commit " + sha
    return "Sin resultado verificable en esta consulta"


def recent_success(runs: list[dict], name: str) -> str:
    """Pages: display last SUCCESS instead of a transient build that will become stale."""
    return recent([item for item in runs if item.get("conclusion") == "success"], name)


def recent_completed(runs: list[dict], name: str) -> str:
    """Do not commit transient queued/running workflow state to the repo."""
    return recent([item for item in runs if item.get("status") == "completed"], name)


def render(root: Path, offline: bool = False, now: dt.datetime | None = None) -> str:
    v = versions(root)
    names = set(git(root, "ls-tree", "-r", "--name-only", "HEAD", "web-pasajero/downloads").splitlines())
    latest_android = github_json("repos/" + REPO + "/actions/runs?branch=main&per_page=70", offline) or {}
    client_actions = github_json("repos/" + REPO + "/actions/workflows/build-cliente-premium-v15.yml/runs?branch=main&per_page=5", offline) or {}
    latest_backend = github_json("repos/" + BACKEND + "/commits/main", offline) or {}
    backend_actions = github_json("repos/" + BACKEND + "/actions/runs?branch=main&per_page=15", offline) or {}
    runs = latest_android.get("workflow_runs", [])
    client_runs = client_actions.get("workflow_runs", [])
    b_runs = backend_actions.get("workflow_runs", [])
    if not isinstance(runs, list): runs = []
    if not isinstance(client_runs, list): client_runs = []
    runs.extend(client_runs)
    if not isinstance(b_runs, list): b_runs = []
    newest_build = latest_app_build(runs)
    newest_change = latest_code_change(offline)
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
      "Este reporte se entrega como artefacto del CI; no se publica como un nuevo commit. La compilación no certifica funcionamiento físico en Samsung.",
      "",
      "| Producto | Versión fuente | Archivo verificable | Última ejecución encontrada |",
      "|---|---|---|---|",
      "| Pasajero PWA | v" + v["pwa"] + " | [Abrir web](" + SITE + ") | " + recent_success(runs, "pages build and deployment") + " |",
      "Solo se distribuyen Web/PWA pasajero y Mapa integrado. **Conductor no es APK independiente.**",
      apk_row("Mapa Trayectos", v["mapa"], mapa_name, "Build Mapa Trayectos RELEASE"),
      "",
      "**PWA CI:** " + recent_completed(runs, "Verify and package passenger PWA (GitHub Pages via branch)") + ".",
      "**Backend fuente:** " + backend_link + " · **último CI encontrado:** " + recent(b_runs, "Traslados Web — CI independiente") + ".",
      "",
      "### Fecha y hora de la última compilación y del último cambio",
      "",
      "**Última compilación de aplicación con resultado SUCCESS:** "
        + (recent([newest_build], newest_build["name"]) + " · " + newest_build["name"]
           if newest_build else "No fue posible verificar una compilación exitosa") + ".",
      "**Último cambio de código verificado:** "
        + (time_uy(newest_change["timestamp"]) + " · "
           + "[" + newest_change["area"] + " / " + newest_change["sha"][:10] + "]("
           + "https://github.com/" + newest_change["repository"] + "/commit/" + newest_change["sha"] + ")"
           + " — " + newest_change["subject"]
           if newest_change else "No fue posible verificar la fecha/hora del último cambio de código")
        + ".",
      "",
      "La hora de compilación corresponde a la **última actualización del workflow en GitHub**; "
      "no es el instante exacto de firma del APK. Los cambios automáticos de documentación "
      "se excluyen de «último cambio de código». Todas las horas son de Montevideo (UYT).",
      "",
      "### Frase para la siguiente sesión",
      "",
      "> **Retomar Traslados Web desde Git**",
      "",
      "Al confirmar el resultado de cada compilación, ofrecer al usuario esta misma frase para copiar y pegar.",
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
