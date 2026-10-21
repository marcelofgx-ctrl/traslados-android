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
    parser.add_argument("--stable-mapa", action="store_true", help="Build updatable Mapa QA using stable ID + CI-provided signing key")
    parser.add_argument("--mapa-version-code", type=int, default=0)
    args = parser.parse_args()
    if re.fullmatch(r"r[0-9]+", args.qa_id) is None:
        raise ValueError("QA ID must be r followed by numeric CI run ID")

    if args.stable_mapa and not (100000 <= args.mapa_version_code <= 2100000000):
        raise ValueError("Stable Mapa QA requires a positive monotonically increasing version code")
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
        if module == "mapatrayectos" and args.stable_mapa:
            # Stable identifier from this QA family onwards; no production overwrite.
            # Signing comes only from a CI secret, never from repository files.
            signing = """    buildTypes {
        debug {
            applicationIdSuffix '.qa'
            versionNameSuffix '-QA'
            if (!hasReleaseSigning) {
                throw new GradleException('Stable Mapa QA requires existing protected signing secrets')
            }
            signingConfig signingConfigs.release
        }
"""
            gradle = once(gradle, "    buildTypes {\\n", signing, gradle_file)
            matches=list(re.finditer(r"(?m)^        versionCode [0-9]+$",gradle))
            if len(matches)!=1:
                raise ValueError("Expected exactly one base Android versionCode in Mapa")
            a=matches[0]
            gradle=gradle[:a.start()]+f"        versionCode {args.mapa_version_code}"+gradle[a.end():]
        else:
            gradle = once(gradle, "    buildTypes {\\n",
                "    buildTypes {\\n        debug { applicationIdSuffix '.qa." +
                args.qa_id + "'; versionNameSuffix '-QA' }\\n",
                gradle_file)
        gradle_file.write_text(gradle, encoding="utf-8")

        manifest = dst / module / "src" / "main" / "AndroidManifest.xml"
        xml = manifest.read_text(encoding="utf-8")
        xml = once(xml, 'android:label="' + original_label + '"',
            'android:label="' + qa_label + '"', manifest)
        manifest.write_text(xml, encoding="utf-8")

        assert 'android:label="' + original_label + '"' in (
            src / module / "src" / "main" / "AndroidManifest.xml"
        ).read_text(encoding="utf-8")
        assert "    buildTypes {\\n" in (src / module / "build.gradle").read_text(encoding="utf-8")

    print("Created isolated Android QA project: " + str(dst))
    print("Mapa QA package: " + ("uy.com.mapatrayectos.qa" if args.stable_mapa
          else "uy.com.mapatrayectos.qa."+args.qa_id))
    if args.stable_mapa:
        print("Mapa QA versionCode: "+str(args.mapa_version_code))
        print("Mapa QA signing: protected existing Android release key; no local or debug key")
    print("Conductor QA package: uy.com.traslados.conductor.qa." + args.qa_id)
    print("Official APK sources and production signing config: UNCHANGED")
    return 0

if __name__ == "__main__":
    sys.exit(main())
