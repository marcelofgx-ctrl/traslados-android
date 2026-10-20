#!/usr/bin/env python3
"""Create a throwaway Android Gradle project for side-by-side Quick Trip QA APKs.

Only in the ephemeral copy:
  - Give Mapa and Conductor unique applicationId suffixes per GitHub run.
  - Rename launcher labels to visibly say QA.
Original sources, signing settings and all engines remain byte-for-byte untouched.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import re
import shutil
import sys

MODULES = (
    ("mapatrayectos", "Mapa Trayectos", "Mapa QA"),
    ("conductor", "Traslados Conductor", "Conductor QA"),
)
ROOT_FILES = ("build.gradle", "settings.gradle", "gradle.properties")

def once(text: str, before: str, after: str, where: Path) -> str:
    if text.count(before) != 1:
        raise ValueError(f"{where}: expected one unambiguous anchor: {before!r}")
    return text.replace(before, after, 1)

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--dest", required=True, type=Path)
    parser.add_argument("--qa-id", required=True)
    args = parser.parse_args()
    if re.fullmatch(r"r[0-9]+", args.qa_id) is None:
        raise ValueError("QA ID must be r followed by numeric CI run ID")

    src = args.source.resolve()
    dst = args.dest.resolve()
    if not src.is_dir() or dst == src or src in dst.parents:
        raise ValueError("QA destination must be a distinct directory, outside the checkout")
    if dst.exists():
        raise ValueError("QA target already exists; never overwrite an earlier build")
    dst.mkdir(parents=True)

    for name in ROOT_FILES:
        from_path = src / name
        if not from_path.is_file():
            raise ValueError("Missing Gradle root config: " + str(from_path))
        shutil.copy2(from_path, dst / name)

    dirs = ("mapatrayectos", "conductor", "core-reservations")
    for name in dirs:
        base = src / name
        if not base.is_dir():
            raise ValueError("Missing Android module: " + str(base))
        shutil.copytree(base, dst / name, ignore=shutil.ignore_patterns(
            "build", ".gradle", "*.iml",
        ))

    for module, original_label, qa_label in MODULES:
        gradle_file = dst / module / "build.gradle"
        gradle = gradle_file.read_text(encoding="utf-8")
        gradle = once(gradle, "    buildTypes {\n",
            "    buildTypes {\n        debug { applicationIdSuffix '.qa." +
            args.qa_id + "'; versionNameSuffix '-QA' }\n",
            gradle_file)
        gradle_file.write_text(gradle, encoding="utf-8")

        manifest = dst / module / "src" / "main" / "AndroidManifest.xml"
        xml = manifest.read_text(encoding="utf-8")
        xml = once(xml, 'android:label="' + original_label + '"',
            'android:label="' + qa_label + '"', manifest)
        manifest.write_text(xml, encoding="utf-8")

        # Assert that untouched original remains the baseline.
        assert 'android:label="' + original_label + '"' in (
            src / module / "src" / "main" / "AndroidManifest.xml"
        ).read_text(encoding="utf-8")
        assert "    buildTypes {\n" in (src / module / "build.gradle").read_text(encoding="utf-8")

    print("Created isolated Android QA project: " + str(dst))
    print("Mapa QA package: uy.com.mapatrayectos.qa." + args.qa_id)
    print("Conductor QA package: uy.com.traslados.conductor.qa." + args.qa_id)
    print("Official APK sources and production signing config: UNCHANGED")
    return 0

if __name__ == "__main__":
    sys.exit(main())
