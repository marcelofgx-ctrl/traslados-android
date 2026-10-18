#!/usr/bin/env python3
"""MON-001/AUTH-001: stale private transfer cards must not survive logout.
Synthetic source contracts only: no network, PIN, database writes or deletion.
"""
from pathlib import Path
root=Path("mapatrayectos/src/main/java/uy/com/mapatrayectos")
alerts=(root/"TransferAlerts.java").read_text()
identity=(root/"DriverIdentity.java").read_text()

def method(src, signature):
    start=src.find(signature)
    assert start>=0, f"Missing method: {signature}"
    begin=src.find("{",start)
    assert begin>=0
    depth=0
    for i in range(begin,len(src)):
        if src[i]=="{": depth+=1
        elif src[i]=="}":
            depth-=1
            if depth==0:return src[begin:i+1]
    raise AssertionError("Unbalanced braces")

# Cached active passenger addresses cannot be read without linked driver.
for signature in (
    "public static JSONArray snapshot(Context c)",
    "public static int pendingCount(Context c)",
    "public static boolean hasUnseen(Context c)",
    "public static String lastText(Context c)",
    "public static long lastSync(Context c)",
):
    body=method(alerts,signature)
    assert "DriverIdentity.pin(c)" in body, f"Missing linked-identity read guard: {signature}"

# A fresh binding or explicit sign out expires display-only cache immediately.
assert "public static void invalidateLocalSession(Context c)" in alerts
for signature in (
    "public static synchronized void bind(Context context,String validatedPin)",
    "public static synchronized void clear(Context context)",
):
    body=method(identity,signature)
    assert "TransferAlerts.invalidateLocalSession(app)" in body, (
        f"Private snapshot persists across identity transition: {signature}"
    )
clearer=method(alerts,"public static void invalidateLocalSession(Context c)")
for key in ('"snapshot"','"pending"','"last_text"','"last_id"','"unseen"','"synced_at"'):
    assert key in clearer,f"Cache field not invalidated: {key}"
assert "TripDb" not in clearer and "TrackDb" not in clearer
assert "DELETE FROM" not in clearer and "truncate" not in clearer.lower()
print("PASS MON-001: linked-identity read guards and display-only cache invalidation")
