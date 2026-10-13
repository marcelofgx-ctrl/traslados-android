"""Contract test for the lightweight Traslados state generator (no production calls)."""
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

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

    def test_missing_version_fails_closed(self):
        (self.root / "web-pasajero/sw.js").write_text("no version", encoding="utf-8")
        with self.assertRaises(ValueError):
            status.versions(self.root)


if __name__ == "__main__":
    unittest.main()
