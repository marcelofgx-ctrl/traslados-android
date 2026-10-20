#!/usr/bin/env python3
"""Create an isolated QA copy of the ORIGINAL premium passenger WebView wrapper.

Never edit the production passenger Gradle or manifest in the checkout.
Never change search/route/booking engines. The QA copy only changes:
  - Android app ID (unique per GitHub run) and displayed app label
  - initial WebView URL (?ui=clean, same official passenger web)
"""
from __future__ import annotations
import argparse
from pathlib import Path
import re
import shutil
import sys

CANONICAL = "https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/"

def rewrite_exact(text: str, old: str, new: str, path: Path) -> str:
    matches = text.count(old)
    if matches != 1:
        raise ValueError(f"{path}: expected exactly one anchor, found {matches}: {old!r}")
    return text.replace(old, new, 1)

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--dest", required=True, type=Path)
    parser.add_argument("--qa-id", required=True, help="r followed by GitHub run ID")
    parser.add_argument("--icon", required=True, type=Path)
    args = parser.parse_args()

    if re.fullmatch(r"r[0-9]+", args.qa_id) is None:
        raise ValueError("QA ID must be r + numeric run ID")
    source = args.source.resolve()
    dest = args.dest.resolve()
    if not source.is_dir() or not (source / "settings.gradle").is_file():
        raise ValueError("Expected premium client Gradle source not found")
    if dest == source or source in dest.parents:
        raise ValueError("QA output must be outside the production source")
    if dest.exists():
        raise ValueError("QA output directory already exists: " + str(dest))
    if not args.icon.is_file() or args.icon.stat().st_size < 300:
        raise ValueError("Official passenger icon is missing")
    shutil.copytree(source, dest, ignore=shutil.ignore_patterns("build", ".gradle"))

    gradle_file = dest / "app" / "build.gradle"
    gradle = gradle_file.read_text(encoding="utf-8")
    gradle = rewrite_exact(
        gradle,
        "debug { applicationIdSuffix '.debug' }",
        "debug { applicationIdSuffix '.qa." + args.qa_id + "'; versionNameSuffix '-QA' }",
        gradle_file,
    )
    gradle = rewrite_exact(gradle, CANONICAL, CANONICAL + "?ui=clean", gradle_file)
    gradle_file.write_text(gradle, encoding="utf-8")

    manifest = dest / "app" / "src" / "main" / "AndroidManifest.xml"
    content = manifest.read_text(encoding="utf-8")
    content = rewrite_exact(content, 'android:label="Traslados"', 'android:label="Quick Trip QA"', manifest)
    manifest.write_text(content, encoding="utf-8")

    icon = dest / "app" / "src" / "main" / "res" / "drawable-nodpi" / "traslados_brand.png"
    icon.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(args.icon, icon)

    # Guard against accidental production source changes.
    original = (source / "app" / "build.gradle").read_text(encoding="utf-8")
    assert "debug { applicationIdSuffix '.debug' }" in original
    assert CANONICAL in original and CANONICAL + "?ui=clean" not in original
    assert 'android:label="Traslados"' in (source / "app" / "src" / "main" / "AndroidManifest.xml").read_text(encoding="utf-8")

    print("QA client project:", dest)
    print("QA package: uy.com.traslados.cliente.qa." + args.qa_id)
    print("QA label: Quick Trip QA")
    print("QA canonical web:", CANONICAL + "?ui=clean")
    print("Production premium passenger source is unchanged.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
