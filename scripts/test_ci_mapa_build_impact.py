#!/usr/bin/env python3
"""No-network regression tests for conservative Android G4 build impact."""
import unittest
from ci_mapa_build_impact import EXECUTED_TESTS, build_decision


class ImpactTest(unittest.TestCase):
    def test_known_test_only_skips_apk(self):
        self.assertFalse(build_decision([("M", "mapatrayectos/tests/NavigationHeadingPolicyTest.java")])[0])

    def test_documentation_plus_known_test_skips(self):
        self.assertFalse(build_decision([
            ("M", "mapatrayectos/tests/QuickReservationLogicTest.java"),
            ("A", "docs/QA_MAPA.md"),
        ])[0])

    def test_all_known_are_actually_named(self):
        self.assertEqual(len(EXECUTED_TESTS), 8)

    def test_native_policy_changed_forces_apk(self):
        self.assertTrue(build_decision([
            ("M", "mapatrayectos/tests/NavigationHeadingPolicyTest.java"),
            ("M", "mapatrayectos/src/main/java/uy/com/mapatrayectos/NavigationHeadingPolicy.java"),
        ])[0])

    def test_conductor_or_shared_code_forces_apk(self):
        for path in ("conductor/src/main/java/MainActivity.java",
                     "core-reservations/src/main/java/X.java",
                     "mapatrayectos/src/main/AndroidManifest.xml",
                     "mapatrayectos/build.gradle", "gradle.properties",
                     ".github/workflows/validate-modular-mapa.yml"):
            with self.subTest(path=path):
                self.assertTrue(build_decision([("M", path)])[0])

    def test_unknown_or_new_test_forces_apk(self):
        self.assertTrue(build_decision([("A", "mapatrayectos/tests/NewTest.java")])[0])

    def test_removed_or_renamed_test_forces_apk(self):
        self.assertTrue(build_decision([("D", "mapatrayectos/tests/QuickReservationLogicTest.java")])[0])
        self.assertTrue(build_decision([("R100", "mapatrayectos/tests/QuickReservationLogicTest.java")])[0])

    def test_docs_only_or_no_diff_conservative(self):
        self.assertTrue(build_decision([("M", "docs/README.md")])[0])
        self.assertTrue(build_decision([])[0])

    def test_other_pwa_code_never_skips(self):
        self.assertTrue(build_decision([("M", "web-pasajero/app.js")])[0])


if __name__ == "__main__":
    unittest.main(verbosity=2)
