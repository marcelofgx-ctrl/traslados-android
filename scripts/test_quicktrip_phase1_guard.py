#!/usr/bin/env python3
"""Red/green mutation tests. All negatives MUST be rejected by the guard.

No git writes, network, Android builds, Supabase, Pages, releases, or PR merge.
"""
from __future__ import annotations

import copy
import json
from pathlib import Path
import sys
import unittest

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
sys.path.insert(0, str(HERE))
import qa_quicktrip_phase1_guard as gate


CANON = json.loads((ROOT / "docs/quicktrip-canonical-contract.json").read_text(
    encoding="utf-8"
))

CANONICAL_SAMPLE = """<!doctype html>
<html lang="es-UY" data-qt-contract="v1">
  <body>
    <header><div data-qt-component="theme-toggle">Modo claro/oscuro</div></header>
    <section data-qt-component="city-hero">Quick Trip</section>
    <button data-qt-service="tu-viaje" data-qt-shape="circular">Tu Viaje</button>
    <button data-qt-service="tu-paquete" data-qt-shape="circular">Tu Paquete</button>
    <button data-qt-service="tu-viaje-programado" data-qt-shape="circular">Tu Viaje Programado</button>
    <main data-qt-component="tu-viaje-compacto">
      <section data-qt-component="route-map">Mapa original</section>
      <details data-qt-component="collapsible-history">Historial</details>
      <button data-qt-cta="solicitar-viaje" data-qt-cta-source="original">Solicitar viaje</button>
    </main>
  </body>
</html>
"""


class Phase1ContractTests(unittest.TestCase):

    def test_01_positive_manifest_is_consistent(self):
        gate.contract_guard(copy.deepcopy(CANON))
        print("GREEN POSITIVE: manifest truthful and canonical")

    def test_02_negative_wrong_base_is_rejected(self):
        d = copy.deepcopy(CANON)
        d["base_commit"] = "0" * 40
        with self.assertRaisesRegex(gate.PolicyFailure, "base SHA"):
            gate.contract_guard(d)
        print("RED EXPECTED: wrong base SHA rejected")

    def test_03_negative_fourth_service_is_rejected(self):
        d = copy.deepcopy(CANON)
        d["design"]["home"]["services"].append("Viajar ahora")
        with self.assertRaisesRegex(gate.PolicyFailure, "three circular services"):
            gate.contract_guard(d)
        print("RED EXPECTED: obsolete fourth service rejected")

    def test_04_negative_legacy_rectangular_cards_are_rejected(self):
        d = copy.deepcopy(CANON)
        d["design"]["home"]["service_shape"] = "rectangular"
        with self.assertRaisesRegex(gate.PolicyFailure, "rectangular"):
            gate.contract_guard(d)
        print("RED EXPECTED: previous rectangular design rejected")

    def test_05_negative_four_step_trip_is_rejected(self):
        d = copy.deepcopy(CANON)
        d["design"]["immediate_trip"]["screen"] = "four_steps"
        with self.assertRaisesRegex(gate.PolicyFailure, "historical"):
            gate.contract_guard(d)
        print("RED EXPECTED: superseded four-step flow rejected")

    def test_06_negative_new_rpc_engine_is_rejected(self):
        d = copy.deepcopy(CANON)
        d["design"]["immediate_trip"]["uses_original_engines"] = False
        with self.assertRaisesRegex(gate.PolicyFailure, "original engines"):
            gate.contract_guard(d)
        print("RED EXPECTED: duplicate booking engine rejected")

    def test_07_negative_motor_sha_mutation_is_rejected(self):
        d = copy.deepcopy(CANON)
        d["protected_blobs"]["web-pasajero/route-engine.js"] = "not-a-sha"
        with self.assertRaisesRegex(gate.PolicyFailure, "invalid Git blob"):
            gate.contract_guard(d)
        print("RED EXPECTED: invalid fingerprint for protected engine rejected")

    def test_08_negative_extra_file_or_apk_is_rejected(self):
        d = copy.deepcopy(CANON)
        d["allowed_phase1_files"].append("mapatrayectos/src/main/java/Example.java")
        with self.assertRaisesRegex(gate.PolicyFailure, "file allowlist"):
            gate.contract_guard(d)
        print("RED EXPECTED: modified Android/app files rejected")

    def test_09_negative_claim_of_physical_samsung_is_rejected(self):
        d = copy.deepcopy(CANON)
        d["evidence"]["real_samsung_validation"] = "approved"
        with self.assertRaisesRegex(gate.PolicyFailure, "Chromium is not physical Samsung"):
            gate.contract_guard(d)
        print("RED EXPECTED: invented Samsung approval rejected")

    def test_10_negative_fake_visual_baseline_is_rejected(self):
        d = copy.deepcopy(CANON)
        d["evidence"]["reference_image_sha256"] = ["0" * 64]
        with self.assertRaisesRegex(gate.PolicyFailure, "not verified"):
            gate.contract_guard(d)
        print("RED EXPECTED: fabricated reference photo hash rejected")

    def test_11_positive_synthetic_candidate_markup(self):
        gate.candidate_markup_guard(CANONICAL_SAMPLE)
        print("GREEN POSITIVE: future candidate fixture meets structural hooks")

    def test_12_negative_real_main_html_is_not_canonical_yet(self):
        old_main = (ROOT / "web-pasajero/index.html").read_text(encoding="utf-8")
        # Deliberately checks REAL main, and expects it to fail the new contract.
        with self.assertRaisesRegex(gate.PolicyFailure, "explicit v1 contract"):
            gate.candidate_markup_guard(old_main)
        print("RED EXPECTED: actual old production HTML rejected as new design")

    def test_13_negative_old_bottom_nav_is_rejected(self):
        d = CANONICAL_SAMPLE.replace("</body>",
            '<nav data-qt-component="old-bottom-nav">legacy</nav></body>')
        with self.assertRaisesRegex(gate.PolicyFailure, "obsolete bottom navigation"):
            gate.candidate_markup_guard(d)
        print("RED EXPECTED: old bottom nav rejected")

    def test_14_negative_duplicate_cta_is_rejected(self):
        d = CANONICAL_SAMPLE.replace("</main>",
            '<button data-qt-cta="solicitar-viaje" data-qt-cta-source="original">Enviar</button></main>')
        with self.assertRaisesRegex(gate.PolicyFailure, "exactly one request CTA"):
            gate.candidate_markup_guard(d)
        print("RED EXPECTED: duplicated submit button rejected")

    def test_15_negative_fake_promotion_is_blocked(self):
        with self.assertRaisesRegex(gate.PolicyFailure, "approved original image"):
            gate.promotion_guard(CANON["evidence"], CANON["base_commit"])
        print("RED EXPECTED: phase 1 cannot pass release gate without approved references")

    def test_16_positive_real_git_motor_hashes_unchanged(self):
        import subprocess
        for filename, expected in CANON["protected_blobs"].items():
            got = subprocess.check_output(
                ["git", "-C", str(ROOT), "rev-parse", "HEAD:" + filename],
                text=True
            ).strip()
            self.assertEqual(got, expected, filename)
        print("GREEN POSITIVE: actual Git motor/blob hashes unchanged")

    def test_17_negative_modified_engine_git_hash_is_rejected(self):
        # Mutate only an in-memory manifest and compare with REAL repository
        # to demonstrate the exact SHA check would fail; no files altered.
        d = copy.deepcopy(CANON)
        target = "web-pasajero/route-engine.js"
        d["protected_blobs"][target] = "a" * 40
        with self.assertRaisesRegex(gate.PolicyFailure, "protected engine/source changed"):
            gate.repository_guard(ROOT, d)
        print("RED EXPECTED: actual Git blob mismatch rejected")

    def test_18_positive_isolated_git_scope_and_actual_source(self):
        record = gate.repository_guard(ROOT, CANON)
        self.assertEqual(set(record["phase1_only"]), gate.REQUIRED_PHASE1)
        self.assertEqual(record["protected_blobs_unchanged"],
                         len(CANON["protected_blobs"]))
        self.assertEqual(record["release_gate"],
                         "BLOCKED_PENDING_VISUAL_AND_SAMSUNG_EVIDENCE")
        print("GREEN POSITIVE: isolated 6 files only; protected source preserved")


if __name__ == "__main__":
    unittest.main(verbosity=2)
