#!/usr/bin/env python3
"""Deterministic web-to-APK assets layer for the Traslados passenger mosaic.

No network access and no dependencies. A native APK contains the same runtime
files as the passenger PWA, except SW registration, test suites and downloads.
Incremental sync preserves unchanged mtimes so local Gradle's input snapshots
and caches can be reused; stale files are removed.
"""
from __future__ import annotations
from pathlib import Path
import hashlib
import json

EXTENSIONS = frozenset({".html", ".js", ".css", ".svg", ".png", ".jpg",
                        ".jpeg", ".webp", ".webmanifest", ".woff", ".woff2", ".json"})
EXCLUDED_DIRS = frozenset({"downloads", "tests", "node_modules", ".git", "__pycache__"})
EXCLUDED_FILES = frozenset({"sw.js"})

def runtime_files(web: Path) -> dict[str, Path]:
    result = {}
    for src in sorted(web.rglob("*")):
        if not src.is_file() or src.is_symlink():
            continue
        rel = src.relative_to(web)
        if any(part in EXCLUDED_DIRS for part in rel.parts[:-1]):
            continue
        if rel.name in EXCLUDED_FILES or src.suffix.lower() not in EXTENSIONS:
            continue
        result[rel.as_posix()] = src
    required = {"index.html", "app.js", "route-engine.js", "eta-engine.js",
                "booking-contracts.js", "trip-board.js", "rpc-client.js"}
    absent = required - result.keys()
    if absent:
        raise ValueError("Required passenger runtime missing: " + ", ".join(sorted(absent)))
    return result

def write_if_changed(destination: Path, data: bytes) -> bool:
    if destination.is_file() and destination.read_bytes() == data:
        return False
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(data)
    return True

def sync_runtime(web: Path, target: Path,
                 overrides: dict[str, bytes] | None = None) -> dict[str, int]:
    """Stage content-addressed files, optionally with derived browser layers.

    The override map is computed from the canonical web source, not from
    previously generated assets. Two identical runs then rewrite NO files.
    """
    items = runtime_files(web)
    overrides = overrides or {}
    for name, data in overrides.items():
        if (name not in items and name != "preview-isolation.css") or not isinstance(data, bytes):
            raise ValueError("Unexpected preview asset override: " + name)
    target.mkdir(parents=True, exist_ok=True)
    desired = set(items) | set(overrides)
    copied = reused = 0
    for rel, src in items.items():
        data = overrides.get(rel)
        if data is None:
            data = src.read_bytes()
        dst = target / rel
        if write_if_changed(dst, data):
            copied += 1
        else:
            reused += 1
    for rel in overrides:
        if rel not in items:
            if write_if_changed(target/rel, overrides[rel]):
                copied += 1
            else:
                reused += 1
    removed = 0
    for dst in sorted(target.rglob("*"), reverse=True):
        if dst.is_file() and dst.relative_to(target).as_posix() not in desired:
            dst.unlink()
            removed += 1
        elif dst.is_dir() and not any(dst.iterdir()):
            dst.rmdir()
    return {"copied": copied, "reused": reused, "removed": removed}

def sha256_manifest(target: Path) -> dict[str, str]:
    return {p.relative_to(target).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted(target.rglob("*")) if p.is_file()}

def manifest_json(target: Path) -> bytes:
    return (json.dumps(sha256_manifest(target), ensure_ascii=False,
                       sort_keys=True, indent=2) + "\n").encode("utf-8")
