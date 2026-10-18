#!/usr/bin/env bash
# G3 fast, source-of-truth contracts only. No Gradle, Android SDK, APK, server or credentials.
set -euo pipefail
cd "$(dirname "$0")/.."
group="${1:-all}"
case "$group" in reservations|navigation|trip|system|all) ;; *)
  printf 'Unknown contract group: %s\n' "$group" >&2; exit 2;;
esac
temp="$(mktemp -d)"
trap 'rm -rf "$temp"' EXIT
begin=$SECONDS

contract() {
  local owner="$1"; shift
  local dir="$temp/$owner"
  mkdir -p "$dir"
  printf 'FAST CONTRACT [%s] -- compiling only pure Java policy/test\n' "$owner"
  javac -encoding UTF-8 -d "$dir" "$@"
  case "$owner" in
    reservations) java -cp "$dir" QuickReservationLogicTest ;;
    navigation) java -cp "$dir" NavigationHeadingPolicyTest ;;
    trip)
      java -cp "$dir" TripPlatformChoiceTest
      java -cp "$dir" TripClosePolicyTest ;;
    system) java -cp "$dir" SystemStatusPolicyTest ;;
  esac
}

run_group() {
  case "$1" in
    reservations) contract reservations \
      core-reservations/src/main/java/uy/com/mapatrayectos/QuickReservationLogic.java \
      mapatrayectos/tests/QuickReservationLogicTest.java ;;
    navigation) contract navigation \
      mapatrayectos/src/main/java/uy/com/mapatrayectos/NavigationHeadingPolicy.java \
      mapatrayectos/tests/NavigationHeadingPolicyTest.java ;;
    trip) contract trip \
      mapatrayectos/src/main/java/uy/com/mapatrayectos/TripPlatformChoice.java \
      mapatrayectos/src/main/java/uy/com/mapatrayectos/TripClosePolicy.java \
      mapatrayectos/tests/TripPlatformChoiceTest.java \
      mapatrayectos/tests/TripClosePolicyTest.java ;;
    system) contract system \
      mapatrayectos/src/main/java/uy/com/mapatrayectos/SystemStatusPolicy.java \
      mapatrayectos/tests/SystemStatusPolicyTest.java ;;
  esac
}
if [[ "$group" == all ]]; then
  for item in reservations navigation trip system; do run_group "$item"; done
else
  run_group "$group"
fi
elapsed=$((SECONDS-begin))
printf 'PASS fast contracts group=%s duration=%ss; no Gradle, Android SDK or APK\n' "$group" "$elapsed"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  printf '### Fast modular contracts\n\nGroup: `%s`. Duration: **%s s**. **No Android build**.\n\nFull integration and Samsung QA remain separate required gates.\n' "$group" "$elapsed" >> "$GITHUB_STEP_SUMMARY"
fi
