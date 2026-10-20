#!/usr/bin/env python3
"""No-network regression tests for G1 PR scope guard."""
import unittest
from ci_pr_scope import ENFORCE_FROM, check_scope, parse_scope

NEW = "2026-10-18T12:01:00Z"
OLD = "2026-10-18T11:41:12Z"


def block(owner, files, reason=None):
    value = "<!-- traslados-scope\nowner: " + owner + "\npaths:\n"
    value += "".join("- " + filename + "\n" for filename in files)
    if reason:
        value += "reason: " + reason + "\n"
    return value + "-->"


class ScopeGuardTest(unittest.TestCase):
    def test_cutoff_preserves_open_review_pr(self):
        self.assertTrue(check_scope("", OLD, ["mapatrayectos/src/X.java"])[0])
        self.assertEqual(ENFORCE_FROM.isoformat(), "2026-10-18T11:57:04+00:00")

    def test_new_pr_requires_declaration(self):
        ok, reason = check_scope("", NEW, ["web-pasajero/app.js"])
        self.assertFalse(ok)
        self.assertIn("requires", reason)

    def test_narrow_ci_pr_passes(self):
        files = ["scripts/ci_pr_scope.py", "scripts/test_ci_pr_scope.py",
                 ".github/workflows/pr-scope-guard.yml", "docs/PR_SCOPE_POLICY.md"]
        self.assertTrue(check_scope(block("ci", files), NEW, files)[0])

    def test_ci_control_schema_is_allowed_only_for_ci(self):
        paths = ["db/quicktrip_control_v1.sql", ".github/workflows/estado-tecnico-auto.yml"]
        self.assertTrue(check_scope(block("ci", paths), NEW, paths)[0])
        self.assertFalse(check_scope(block("pwa", paths), NEW, paths)[0])

    def test_undeclared_file_is_rejected(self):
        files = ["web-pasajero/app.js", "web-pasajero/sw.js"]
        ok, msg = check_scope(block("pwa", files[:1]), NEW, files)
        self.assertFalse(ok)
        self.assertIn("web-pasajero/sw.js", msg)

    def test_extra_listed_file_is_rejected(self):
        ok, msg = check_scope(block("pwa", ["web-pasajero/app.js", "web-pasajero/sw.js"]),
                              NEW, ["web-pasajero/app.js"])
        self.assertFalse(ok)
        self.assertIn("not in PR", msg)

    def test_wrong_owner_is_rejected(self):
        file = "mapatrayectos/src/MainActivity.java"
        ok, msg = check_scope(block("pwa", [file]), NEW, [file])
        self.assertFalse(ok)
        self.assertIn("outside owner", msg)

    def test_cross_surface_requires_explicit_justification(self):
        files = ["web-pasajero/app.js", "mapatrayectos/src/MainActivity.java"]
        self.assertFalse(check_scope(block("mapa", files), NEW, files)[0])
        self.assertFalse(check_scope(block("cross-surface", files), NEW, files)[0])
        self.assertTrue(check_scope(block("cross-surface", files,
                                         "End-to-end agreement between web and Mapa"),
                                    NEW, files)[0])

    def test_broad_wildcards_not_accepted(self):
        with self.assertRaises(ValueError):
            parse_scope(block("ci", ["scripts/**"]))
        with self.assertRaises(ValueError):
            parse_scope(block("ci", ["../outside.txt"]))

    def test_duplicate_or_bad_declared_fields(self):
        with self.assertRaises(ValueError):
            parse_scope(block("ci", ["docs/X.md", "docs/X.md"]))
        with self.assertRaises(ValueError):
            parse_scope("<!-- traslados-scope\nowner: ci\nowner: pwa\npaths:\n"
                        "- docs/X.md\n-->")

    def test_no_files_is_not_a_success(self):
        self.assertFalse(check_scope(block("docs", ["docs/file.md"]), NEW, [])[0])


if __name__ == "__main__":
    unittest.main(verbosity=2)
