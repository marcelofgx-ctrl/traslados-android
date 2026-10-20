#!/usr/bin/env python3
"""G1 PR scope guard: exact paths + logical owner, no product builds or secrets."""
import datetime as dt
import fnmatch
import json
import os
from pathlib import Path
import re
import subprocess
import sys

# Existing stacked review PRs remain untouched. A new PR must declare its G1 scope.
ENFORCE_FROM = dt.datetime.fromisoformat("2026-10-18T11:57:04+00:00")
BLOCK_RE = re.compile(r"<!--\s*traslados-scope\s*\n(.*?)\n\s*-->", re.S | re.I)
OWNER_ROUTES = {
    "ci": [".github/workflows/**", "scripts/**", "tests/**", "docs/**", "db/**", "AGENTS.md"],
    "docs": ["docs/**", "README.md", "AGENTS.md"],
    "pwa": ["web-pasajero/**", "docs/**", "scripts/**", ".github/workflows/**"],
    "mapa": ["mapatrayectos/**", "conductor/src/**", "core-reservations/**",
             "docs/**", "scripts/**", ".github/workflows/**"],
    "cross-surface": ["web-pasajero/**", "mapatrayectos/**", "conductor/src/**",
                      "core-reservations/**", "docs/**", "scripts/**",
                      ".github/workflows/**"],
}
SCOPE_KEYS = {"owner", "paths", "reason"}


def parse_scope(body):
    """Strict and intentionally small format in the PR body, not in PR code."""
    blocks = BLOCK_RE.findall(body or "")
    if not blocks:
        return None
    if len(blocks) != 1:
        raise ValueError("Exactly one traslados-scope block is allowed")
    fields = {}
    key = None
    for raw in blocks[0].splitlines():
        line = raw.strip()
        if not line:
            continue
        if line.startswith("- "):
            if key != "paths":
                raise ValueError("A path entry appears outside paths:")
            fields.setdefault("paths", []).append(line[2:].strip())
            continue
        if ":" not in line:
            raise ValueError("Invalid scope declaration line")
        name, value = (v.strip() for v in line.split(":", 1))
        if name not in SCOPE_KEYS or name in fields:
            raise ValueError("Unknown or repeated declaration key: " + name)
        if name == "paths":
            if value:
                raise ValueError("paths: must be followed by - filename lines")
            fields["paths"] = []
        else:
            fields[name] = value
        key = name
    if fields.get("owner") not in OWNER_ROUTES:
        raise ValueError("owner must be one of: " + ", ".join(OWNER_ROUTES))
    paths = fields.get("paths", [])
    if not paths:
        raise ValueError("At least one exact changed filename is required")
    for path in paths:
        if (path.startswith("/") or path.startswith("./") or
                "\\" in path or "*" in path or "?" in path or
                "[" in path or "]" in path or
                ".." in path.split("/") or not path or path.endswith("/")):
            raise ValueError("Invalid path (use exact repo-relative filenames): " + path)
    if len(set(paths)) != len(paths):
        raise ValueError("Duplicate filename in scope")
    return fields


def check_scope(body, created_at, changed):
    """Return (pass, message); old PRs get grandfathered, not silently promoted."""
    try:
        created = dt.datetime.fromisoformat(created_at.replace("Z", "+00:00"))
        declared = parse_scope(body)
        if declared is None:
            if created < ENFORCE_FROM:
                return True, "LEGACY PR: no scope declaration required; existing QA unchanged"
            return False, "New PR requires a G1 <!-- traslados-scope ... --> declaration"
        if not changed:
            return False, "No changed files found; cannot validate scope"
        allowed = set(declared["paths"])
        unlisted = sorted(set(changed) - allowed)
        extra = sorted(allowed - set(changed))
        owner = declared["owner"]
        outside_owner = sorted(path for path in changed if not any(
            fnmatch.fnmatchcase(path, route) for route in OWNER_ROUTES[owner]))
        product_areas = set()
        for path in changed:
            if path.startswith("web-pasajero/"):
                product_areas.add("pwa")
            if path.startswith(("mapatrayectos/", "conductor/src/", "core-reservations/")):
                product_areas.add("android")
        errors = []
        if unlisted:
            errors.append("Files outside declared scope: " + ", ".join(unlisted))
        if extra:
            errors.append("Declared filenames not in PR: " + ", ".join(extra))
        if outside_owner:
            errors.append("Files outside owner " + owner + ": " + ", ".join(outside_owner))
        if len(product_areas) > 1 and owner != "cross-surface":
            errors.append("Web + Android changed together: owner must be cross-surface")
        if owner == "cross-surface" and len(declared.get("reason", "")) < 20:
            errors.append("Cross-surface changes need a reason of at least 20 characters")
        if errors:
            return False, " | ".join(errors)
        return True, f"Scope OK: owner={owner}; {len(changed)} exact changed files"
    except (ValueError, TypeError, AttributeError) as exc:
        return False, "Invalid PR scope: " + str(exc)


def main():
    event_path = os.environ.get("GITHUB_EVENT_PATH")
    if not event_path:
        print("GITHUB_EVENT_PATH missing: use scripts/test_ci_pr_scope.py for local tests",
              file=sys.stderr)
        return 2
    event = json.loads(Path(event_path).read_text(encoding="utf-8"))
    pr = event["pull_request"]
    base = pr["base"]["sha"]
    head = pr["head"]["sha"]
    # --no-renames shows both old and new paths, avoiding rename scope bypass.
    command = ["git", "diff", "--no-renames", "--name-only", "-z",
               f"{base}...{head}"]
    result = subprocess.run(command, check=True, capture_output=True)
    changed = [p.decode("utf-8") for p in result.stdout.split(b"\0") if p]
    passed, message = check_scope(pr.get("body") or "", pr["created_at"], changed)
    report = ("### G1 declared PR scope\n\n"
              f"**{'PASS' if passed else 'FAIL'}**: {message}\n\n"
              f"Files checked: {len(changed)}. No Gradle, Android SDK, "
              "APK, Supabase queries or product data used.\n")
    print(report)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as handle:
            handle.write(report)
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
