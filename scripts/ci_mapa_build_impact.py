#!/usr/bin/env python3
"""Conservative G4 classifier: no Android APK only for existing, executed test-only edits.

No Gradle, SDK, network, secrets, Supabase or product data; fail closed on uncertainty.
"""
import json
import os
import subprocess
from pathlib import Path

EXECUTED_TESTS = frozenset({
    "mapatrayectos/tests/QuickReservationLogicTest.java",
    "mapatrayectos/tests/SystemStatusPolicyTest.java",
    "mapatrayectos/tests/TripPlatformChoiceTest.java",
    "mapatrayectos/tests/TripClosePolicyTest.java",
    "mapatrayectos/tests/NavigationHeadingPolicyTest.java",
    "mapatrayectos/tests/MetricTypographyPolicyTest.java",
    "mapatrayectos/tests/ShareLinkPolicyTest.java",
    "mapatrayectos/tests/chat-flow-contract-smoke.py",
})


def build_decision(changes):
    """Changes are (status, repo-relative path) pairs from no-renames git diff."""
    if not changes:
        return True, "empty or unverified diff"
    tests = 0
    for status, path in changes:
        if not status or not path or status[0] not in "AM":
            return True, f"unsafe change status for {path or '(empty path)'}"
        if path in EXECUTED_TESTS:
            tests += 1
            continue
        if path.startswith("docs/") and not path.endswith("/"):
            continue
        return True, f"Android or unclassified path: {path}"
    if not tests:
        return True, "no executed native contract test changed"
    return False, f"only {tests} already-executed test path(s) and documentation changed"


def changed_paths(base_sha, head_sha):
    result = subprocess.run(
        ["git", "diff", "--no-renames", "--name-status", "-z", f"{base_sha}...{head_sha}"],
        check=True, capture_output=True,
    )
    parts = [item.decode("utf-8") for item in result.stdout.split(b"\0") if item]
    if len(parts) % 2:
        raise ValueError("unexpected Git diff format")
    return list(zip(parts[::2], parts[1::2]))


def decide_from_environment():
    if os.environ.get("GITHUB_EVENT_NAME") != "pull_request":
        return True, "manual or unknown event: always verify Android integration"
    event_file = os.environ.get("GITHUB_EVENT_PATH")
    if not event_file:
        return True, "missing GitHub event: fail closed"
    try:
        pr = json.loads(Path(event_file).read_text(encoding="utf-8"))["pull_request"]
        base, head = pr["base"]["sha"], pr["head"]["sha"]
        if not base or not head:
            return True, "missing base/head SHA: fail closed"
        return build_decision(changed_paths(base, head))
    except (KeyError, ValueError, OSError, subprocess.CalledProcessError) as exc:
        return True, f"cannot verify PR diff ({type(exc).__name__}): fail closed"


def main():
    needed, reason = decide_from_environment()
    print(f"G4 Android build: {'REQUIRED' if needed else 'SKIPPED TEST-ONLY'}; {reason}")
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
            output.write(f"build_required={'true' if needed else 'false'}\n")
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary:
            summary.write("### G4 conservative Android build selection\n\n")
            summary.write(f"**{'APK integration required' if needed else 'Test-only; no APK build'}**: {reason}.\n\n")
            summary.write("Executed Java/source-contract tests remain mandatory. ")
            summary.write("All native changes still require assembleDebug on the exact SHA.\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
