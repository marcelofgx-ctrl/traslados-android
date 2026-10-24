#!/usr/bin/env python3
"""Quick Trip phase 1, source-only and fail-closed policy gate.

No networks, writes, Supabase, builds, screenshots or deployments.
"verify" inspects current Git HEAD and an explicit canonical JSON contract.
"check_candidate_html" is deliberately a *future integration prototype*,
not a claim that the actual main page already satisfies the visual contract.
"""
from __future__ import annotations

import argparse
import copy
from html.parser import HTMLParser
import json
from pathlib import Path
import re
import subprocess

BASE = "888d0696ebff98773e61039e3df6e46efd872317"
SCHEMA = "quicktrip.phase1.visual-and-scope.v1"
SURFACES = (
    "public_website",
    "passenger_web_pwa",
    "single_mapa_android_with_integrated_conductor",
)
SERVICES = ("Tu Viaje", "Tu Paquete", "Tu Viaje Programado")
SERVICE_IDS = ("tu-viaje", "tu-paquete", "tu-viaje-programado")
PROTECTED_BACKUP = "backup/continuidad-20261024-main"
REQUIRED_PHASE1 = frozenset({
    ".github/workflows/qa-quicktrip-phase1-canonical.yml",
    "docs/PRODUCTO_CANONICO_QUICKTRIP.md",
    "docs/INVENTARIO_FUENTES_QUICKTRIP_20261024.md",
    "docs/quicktrip-canonical-contract.json",
    "scripts/qa_quicktrip_phase1_guard.py",
    "scripts/test_quicktrip_phase1_guard.py",
})
HEX40 = re.compile(r"^[0-9a-f]{40}$")
HEX64 = re.compile(r"^[0-9a-f]{64}$")


class PolicyFailure(Exception):
    """An expected fail-closed security or contract rejection."""


def insist(condition: bool, message: str) -> None:
    if not condition:
        raise PolicyFailure(message)


def contract_guard(data: dict) -> None:
    """Source-of-truth structural contract, not a screenshot-based approval."""
    insist(isinstance(data, dict), "manifest must be a JSON object")
    insist(data.get("schema") == SCHEMA, "unknown canonical contract schema")
    insist(data.get("base_commit") == BASE, "wrong base SHA")
    insist(data.get("protected_backup") == PROTECTED_BACKUP, "wrong protected backup")
    insist(data.get("branch") == "qa/quicktrip-blindaje-fase1-canonico-20261024",
           "unexpected phase-1 branch")
    insist(data.get("surfaces") == list(SURFACES),
           "three official surfaces only; never standalone Driver/Passenger APK")
    d = data.get("design", {})
    insist(d.get("brand") == "Quick Trip", "brand changed")
    insist(d.get("go_accent") == "small_superscript", "go mark changed")
    insist(d.get("theme_modes") == ["dark", "light"], "both theme modes required")
    home = d.get("home", {})
    insist(home.get("hero") == "city_background", "approved city hero missing")
    insist(home.get("service_shape") == "circular", "legacy rectangular service cards forbidden")
    insist(home.get("services") == list(SERVICES), "exact three circular services required")
    insist(home.get("deprecated_services") == ["Viajar ahora"],
           "must prohibit obsolete fourth service")
    immediate = d.get("immediate_trip", {})
    insist(immediate.get("screen") == "single_compact_screen",
           "historical 1/4-4/4 flow is not the new immediate screen")
    insist(immediate.get("header") == "Quick Trip", "wrong trip header")
    required = {
        "origin", "destination", "verified_map", "verified_distance", "verified_price",
        "pickup_eta_if_available", "collapsible_history", "single_request_cta",
    }
    insist(set(immediate.get("required_components", [])) == required,
           "missing or duplicated critical trip components")
    insist(immediate.get("driver_decision") == ["accept", "reject"],
           "immediate request may only be accepted or rejected")
    insist(immediate.get("uses_original_engines") is True, "must reuse original engines")
    scheduled = d.get("scheduled_trip", {})
    insist(scheduled.get("flow") == "separate_from_immediate",
           "scheduled trips must keep a separate flow")
    insist(scheduled.get("uses_original_engines") is True,
           "scheduled trips must reuse original engines")
    owner = d.get("visual_owner", {})
    insist(owner.get("target") == "one_active_stylesheet",
           "multiple CSS presentation owners forbidden")
    insist(owner.get("status") == "requires_future_reconciliation",
           "phase 1 cannot falsely claim that CSS has already been consolidated")
    history = data.get("historical_priority", [])
    dated = {item.get("source"): item for item in history if isinstance(item, dict)}
    current = "docs/TU_VIAJE_PANTALLA_UNICA_CONTRATO_20261020.md"
    old = "docs/CONTRATO_VISUAL_TU_VIAJE_4_PANTALLAS_20261019.md"
    insist(dated.get(current, {}).get("active_for_new_immediate_screen") is True,
           "newer single-screen decision not active")
    insist(dated.get(old, {}).get("active_for_new_immediate_screen") is False,
           "old 4-step reference must stay historical")
    blobs = data.get("protected_blobs", {})
    insist(len(blobs) >= 7, "protected engine manifest incomplete")
    for name, sha in blobs.items():
        insist(HEX40.fullmatch(sha or "") is not None, "invalid Git blob SHA for " + name)
        insist(name.startswith(("web-pasajero/", "mapatrayectos/")),
               "unexpected protected source path")
    allowed = data.get("allowed_phase1_files", [])
    insist(set(allowed) == REQUIRED_PHASE1 and len(allowed) == len(REQUIRED_PHASE1),
           "phase1 file allowlist was modified")
    evidence = data.get("evidence", {})
    insist(evidence.get("original_visual_baselines") == "not_located_in_accessible_sources",
           "visual baseline must not be fabricated during phase 1")
    insist(evidence.get("reference_image_sha256") == [],
           "private source photos not verified; hashes must remain absent")
    insist(evidence.get("real_samsung_validation") == "pending",
           "Chromium is not physical Samsung validation")
    insist(evidence.get("release_approved") is False,
           "promotion has not been authorized")
    insist(evidence.get("page_deployment_approved") is False,
           "deployment has not been authorized")


def promotion_guard(evidence: dict, candidate_sha: str) -> None:
    """Stricter future gate: deliberately red for this phase's evidence."""
    insist(HEX40.fullmatch(candidate_sha or "") is not None,
           "exact candidate Git SHA required")
    hashes = evidence.get("reference_image_sha256", [])
    insist(isinstance(hashes, list) and len(hashes) >= 2 and
           all(isinstance(x, str) and HEX64.fullmatch(x) for x in hashes),
           "approved original image SHA256 evidence missing")
    insist(evidence.get("original_visual_baselines") == "private_refs_verified",
           "original private visual baselines not verified")
    insist(evidence.get("real_samsung_validation") == "approved",
           "physical Samsung sign-off missing")
    insist(evidence.get("release_approved") is True,
           "release approval not granted")
    insist(evidence.get("page_deployment_approved") is True,
           "deployment approval not granted")


class CandidateDOM(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.attrs = []

    def handle_starttag(self, tag, attrs) -> None:
        self.attrs.append((tag, dict(attrs)))


def candidate_markup_guard(source: str) -> None:
    """Future structural UI gate using explicit data-qt-* semantic hooks.

    Contract hooks must be added only in a future authorized UI candidate.
    A synthetic fixture can PASS; current main should FAIL.
    """
    p = CandidateDOM()
    p.feed(source)
    a = [values for _, values in p.attrs]
    def values(key):
        return [d[key] for d in a if key in d]
    insist(values("data-qt-contract").count("v1") == 1,
           "candidate must expose one explicit v1 contract")
    insist(values("data-qt-component").count("city-hero") == 1,
           "single city hero required")
    insist(values("data-qt-component").count("theme-toggle") == 1,
           "single theme toggle required")
    insist(values("data-qt-service") == list(SERVICE_IDS),
           "exact 3 circular services required in order; fourth service rejected")
    for d in a:
        if "data-qt-service" in d:
            insist(d.get("data-qt-shape") == "circular",
                   "legacy rectangular service card rejected")
    insist(values("data-qt-component").count("tu-viaje-compacto") == 1,
           "exactly one compact Tu Viaje screen required")
    insist(values("data-qt-component").count("route-map") == 1,
           "exactly one original map required")
    insist(values("data-qt-component").count("collapsible-history") == 1,
           "history must be collapsible, not duplicated")
    insist(values("data-qt-cta") == ["solicitar-viaje"],
           "exactly one request CTA required")
    insist(values("data-qt-cta-source") == ["original"],
           "booking must use original handler, never QA demo engine")
    insist("legacy-four-steps" not in values("data-qt-component"),
           "old four-step navigation forbidden")
    insist("old-bottom-nav" not in values("data-qt-component"),
           "obsolete bottom navigation forbidden")


def git(root: Path, *args: str) -> str:
    return subprocess.check_output(
        ["git", "-C", str(root), *args],
        stderr=subprocess.STDOUT, text=True
    ).strip()


def repository_guard(root: Path, data: dict) -> dict:
    """Check committed snapshot against base, WITHOUT modifying working tree."""
    contract_guard(data)
    insist(git(root, "rev-parse", "--verify", "HEAD^{commit}") != BASE,
           "phase1 must be in a new isolated commit, not on main")
    base_sha = git(root, "rev-parse", "--verify", BASE + "^{commit}")
    insist(base_sha == BASE, "base commit unavailable or incorrect")
    insist(git(root, "merge-base", BASE, "HEAD") == BASE,
           "phase1 branch does not descend from exactly authorized base")
    changed = git(root, "diff", "--no-renames", "--name-only", BASE + "..HEAD").splitlines()
    insist(set(changed) == REQUIRED_PHASE1,
           "unexpected or missing committed files: " +
           json.dumps({"extra": sorted(set(changed) - REQUIRED_PHASE1),
                       "missing": sorted(REQUIRED_PHASE1 - set(changed))}))
    for filename, expected in data["protected_blobs"].items():
        current = git(root, "rev-parse", "HEAD:" + filename)
        insist(current == expected, "protected engine/source changed: " + filename)
    return {"base": BASE, "head": git(root, "rev-parse", "HEAD"),
            "phase1_only": sorted(changed),
            "protected_blobs_unchanged": len(data["protected_blobs"]),
            "release_gate": "BLOCKED_PENDING_VISUAL_AND_SAMSUNG_EVIDENCE"}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("command", choices=["verify", "manifest"])
    ap.add_argument("--root", type=Path,
                    default=Path(__file__).resolve().parents[1])
    ap.add_argument("--manifest", type=Path,
                    default=Path("docs/quicktrip-canonical-contract.json"))
    args = ap.parse_args()
    root = args.root.resolve()
    manifest = args.manifest if args.manifest.is_absolute() else root / args.manifest
    try:
        data = json.loads(manifest.read_text(encoding="utf-8"))
        if args.command == "verify":
            result = repository_guard(root, data)
        else:
            contract_guard(data)
            result = {"contract": "PASS", "release_gate": "BLOCKED_BY_DESIGN"}
        print("PASS PHASE1 POLICY " + json.dumps(result, sort_keys=True))
        return 0
    except (PolicyFailure, subprocess.CalledProcessError, ValueError, OSError) as exc:
        print("BLOCKED PHASE1 POLICY " + str(exc))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
