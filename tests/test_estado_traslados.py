"""Contract test for the lightweight Traslados state generator (no production calls)."""
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / "scripts/estado_traslados.py"
spec = importlib.util.spec_from_file_location("estado_traslados", SCRIPT)
status = importlib.util.module_from_spec(spec)
spec.loader.exec_module(status)


class EstadoAutomaticoTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        sources = {
            "conductor/build.gradle": "versionCode 128\nversionName '11.5-R6'\n",
            "mapatrayectos/build.gradle": "versionCode 52\nversionName '0.1-R25.0'\n",
            "cliente-premium/app/build.gradle": "versionCode 203\nversionName '15.0-Premium'\n",
            "web-pasajero/sw.js": "const CACHE='traslados-cliente-pwa-v68';\n",
        }
        for path, content in sources.items():
            dest = self.root / path
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_text(content, encoding="utf-8")
        self.git("init", "-q")
        self.git("add", ".")
        self.git("-c", "user.name=test", "-c", "user.email=test@example.invalid",
                 "commit", "-qm", "Fixture versions")

    def tearDown(self):
        self.temp.cleanup()

    def git(self, *arguments):
        subprocess.run(["git", *arguments], cwd=self.root, check=True, capture_output=True)

    def test_extracts_all_versions_from_sources(self):
        v = status.versions(self.root)
        self.assertEqual(v["pwa"], "68")
        self.assertEqual(v["conductor"], ("11.5-R6", "128"))
        self.assertEqual(v["mapa"], ("0.1-R25.0", "52"))
        self.assertEqual(v["cliente"], ("15.0-Premium", "203"))

    def test_offline_state_is_honest_and_idempotent(self):
        a = status.render(self.root, offline=True)
        b = status.render(self.root, offline=True)
        self.assertEqual(a, b, "Repeated runs must not create duplicate documentation commits")
        self.assertIn("PWA | v68", a)
        self.assertIn("11.5-R6", a)
        self.assertIn("NO PUBLICADA EN REPOSITORIO", a)
        self.assertIn("No consultado", a)
        self.assertIn("Supabase", a)
        self.assertNotIn("test@example.invalid", a)

    def test_exact_apk_path_uses_version(self):
        d = self.root / "web-pasajero/downloads"
        d.mkdir(parents=True, exist_ok=True)
        (d / "traslados-conductor-r6.apk").write_bytes(b"fixture-for-name-only")
        self.git("add", ".")
        self.git("-c", "user.name=test", "-c", "user.email=test@example.invalid",
                 "commit", "-qm", "Add fake tracked path")
        value = status.render(self.root, offline=True)
        self.assertIn("downloads/traslados-conductor-r6.apk", value)
        self.assertIn("NO PUBLICADA EN REPOSITORIO", value, "Other APKs remain unverified")

    def test_formats_build_and_change_times_in_uruguay(self):
        self.assertEqual(status.time_uy("2026-10-13T05:36:21Z"), "13/10/2026 02:36 UYT")
        self.assertEqual(status.time_uy("2026-10-13T11:00:00+00:00"), "13/10/2026 08:00 UYT")
        self.assertEqual(status.time_uy(None), "fecha/hora no informada")
        self.assertEqual(status.build_time({
            "status": "completed", "updated_at": "2026-10-13T05:36:21Z"
        }), "13/10/2026 02:36 UYT")

    def test_latest_successful_compilation_excludes_failure_and_docs_pages(self):
        runs = [
            {"name": "pages build and deployment", "head_branch": "main",
             "conclusion": "success", "status": "completed",
             "updated_at": "2026-10-13T11:00:00Z"},
            {"name": "Build Traslados Conductor RELEASE", "head_branch": "main",
             "conclusion": "success", "status": "completed",
             "updated_at": "2026-10-13T05:36:21Z"},
            {"name": "Build Mapa Trayectos RELEASE", "head_branch": "main",
             "conclusion": "failure", "status": "completed",
             "updated_at": "2026-10-13T12:00:00Z"},
        ]
        self.assertEqual(status.latest_app_build(runs)["name"], "Build Traslados Conductor RELEASE")

    def test_ci_status_ignores_transient_builds(self):
        latest = {"name": "Verify and package passenger PWA (GitHub Pages via branch)",
                  "head_branch": "main", "status": "in_progress",
                  "created_at": "2026-10-13T13:00:00Z", "html_url": "https://github.com/test"}
        previous = {"name": latest["name"], "head_branch": "main",
                    "status": "completed", "conclusion": "success",
                    "updated_at": "2026-10-13T12:00:00Z",
                    "html_url": "https://github.com/test"}
        self.assertEqual(status.recent_completed([latest, previous], latest["name"]),
                         status.recent([previous], latest["name"]))
        self.assertIn("SUCCESS", status.recent_completed([latest, previous], latest["name"]))

    def test_latest_code_change_is_not_docs_bot(self):
        fixture = {"sha": "a" * 40, "commit": {
            "message": "Actualización PWA v68", "committer": {"date": "2026-10-13T05:34:35Z"}}}
        def fake_api(path, offline=False):
            return [fixture] if "path=web-pasajero" in path else []
        with patch.object(status, "github_json", side_effect=fake_api):
            changed = status.latest_code_change(offline=False)
        self.assertIsNotNone(changed)
        self.assertEqual(changed["area"], "Web pasajero")
        self.assertEqual(status.time_uy(changed["timestamp"]), "13/10/2026 02:34 UYT")
        self.assertIsNone(status.latest_code_change(offline=True))

    def test_result_contains_restart_phrase_and_timing_policy(self):
        value = status.render(self.root, offline=True)
        self.assertIn("Retomar Traslados Web desde Git", value)
        self.assertIn("Última compilación", value)
        self.assertIn("Último cambio de código", value)
        self.assertIn("Montevideo", value)
        self.assertIn("No fue posible verificar", value)

    def test_missing_version_fails_closed(self):
        (self.root / "web-pasajero/sw.js").write_text("no version", encoding="utf-8")
        with self.assertRaises(ValueError):
            status.versions(self.root)


if __name__ == "__main__":
    unittest.main()
