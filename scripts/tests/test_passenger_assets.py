#!/usr/bin/env python3
"""Fast no-network regression for incremental passenger mosaic assets."""
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))
from passenger_assets import runtime_files, sync_runtime, write_if_changed, sha256_manifest, manifest_json

class PassengerAssetsTest(unittest.TestCase):
    def test_incremental_idempotency_cleanup_and_no_apks(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp)
            web=p/"web"
            out=p/"apk"
            (web/"tests").mkdir(parents=True)
            (web/"downloads").mkdir(parents=True)
            (web/"icons").mkdir(parents=True)
            for name in ("index.html","app.js","route-engine.js","eta-engine.js","booking-contracts.js",
                         "trip-board.js","rpc-client.js"):
                (web/name).write_text(name)
            (web/"icons"/"icon-192.png").write_bytes(b"\x89PNGtest")
            (web/"tests"/"code-smoke.cjs").write_text("secret_test")
            (web/"downloads"/"old.apk").write_bytes(b"SECRET_HUGE_APK")
            (web/"sw.js").write_text("must not embed")
            self.assertNotIn("sw.js",runtime_files(web))
            a=sync_runtime(web,out)
            self.assertEqual(a["reused"],0)
            self.assertEqual(a["removed"],0)
            self.assertFalse((out/"downloads").exists())
            self.assertFalse((out/"tests").exists())
            self.assertFalse((out/"sw.js").exists())
            self.assertTrue((out/"icons"/"icon-192.png").is_file())
            (out/"preview-isolation.css").write_text("generated")
            original=sha256_manifest(out)
            m=manifest_json(out)
            self.assertTrue(m.startswith(b"{"))
            self.assertFalse(write_if_changed(out/"index.html",b"index.html"))
            second=sync_runtime(web,out)
            self.assertEqual(second["copied"],0)
            self.assertEqual(second["removed"],0)
            self.assertEqual(sha256_manifest(out),original)
            self.assertEqual(manifest_json(out),m)
            (web/"icons"/"icon-192.png").unlink()
            (web/"route-engine.js").write_text("updated")
            third=sync_runtime(web,out)
            self.assertEqual(third["copied"],1)
            self.assertEqual(third["removed"],1)
            self.assertFalse((out/"icons"/"icon-192.png").exists())
            self.assertTrue((out/"preview-isolation.css").is_file())
            self.assertNotEqual(manifest_json(out),m)
            self.assertTrue(write_if_changed(out/"route-engine.js",b"v2"))
            self.assertFalse(write_if_changed(out/"route-engine.js",b"v2"))

    def test_required_runtime_fail_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaisesRegex(ValueError,"Required passenger runtime"):
                sync_runtime(Path(tmp),Path(tmp)/"out")

if __name__=="__main__":
    unittest.main()
