#!/usr/bin/env bash
set -euo pipefail
# One-time atomic source/resource relocation. Executed ONLY on the draft branch.
# Never run against main or delete signing/data files.
test -f conductor/src/main/java/uy/com/traslados/conductor/MainActivity.java || {
  test -f feature/driver/src/main/java/uy/com/traslados/conductor/MainActivity.java
  echo "Driver source already moved"; exit 0;
}
test -f feature/driver/src/main/java/uy/com/traslados/conductor/DriverHostBridge.java
test -f conductor/src/main/res/drawable-nodpi/app_icon.png

mkdir -p feature/driver/src/main/java/uy/com/traslados/conductor
for java in conductor/src/main/java/uy/com/traslados/conductor/*.java; do
  git mv "$java" "feature/driver/src/main/java/uy/com/traslados/conductor/$(basename "$java")"
done
for dir in drawable raw values; do
  git mv "conductor/src/main/res/$dir" "feature/driver/src/main/res/$dir"
done
mkdir -p feature/driver/src/main/res/drawable-nodpi
git mv conductor/src/main/res/drawable-nodpi/app_icon.png \
  feature/driver/src/main/res/drawable-nodpi/conductor_app_icon.png
git mv conductor/src/main/res/drawable-nodpi/bg_texture.png \
  feature/driver/src/main/res/drawable-nodpi/conductor_bg_texture.png

# Standalone Conductor alone owns its application icon; the shared library
# exposes a uniquely named icon for the UI hosted by either Mapa or Conductor.
mkdir -p conductor/src/bootstrap/res/drawable-nodpi
cp feature/driver/src/main/res/drawable-nodpi/conductor_app_icon.png \
  conductor/src/bootstrap/res/drawable-nodpi/app_icon.png

python3 - <<'PY'
from pathlib import Path
p=Path('feature/driver/build.gradle')
c=p.read_text()
old_java="java.setSrcDirs(['../../conductor/src/main/java', 'src/main/java'])"
old_res='res.setSrcDirs([\'../../conductor/src/main/res\', "$buildDir/generated/driverOnlyRes"])'
assert old_java in c and old_res in c, 'Unexpected library source layout'
c=c.replace(old_java, "java.setSrcDirs(['src/main/java'])")
c=c.replace(old_res,"res.setSrcDirs(['src/main/res'])")
start=c.find('// The artwork is kept byte-for-byte;')
assert start>=0
c=c[:start]+"// All Java, XML, audio and artwork are owned by this library.\n"
p.write_text(c)

p=Path('.github/workflows/build-mapa-mosaic-r25-27.yml')
c=p.read_text().replace('conductor/src/main/java/uy/com/traslados/conductor/', 'feature/driver/src/main/java/uy/com/traslados/conductor/')
needle="      - 'mapatrayectos/build.gradle'"
assert needle in c
c=c.replace(needle,needle+"\n      - 'feature/driver/**'")
p.write_text(c)
PY
test ! -e conductor/src/main/java/uy/com/traslados/conductor/MainActivity.java
test -f feature/driver/src/main/java/uy/com/traslados/conductor/MainActivity.java
test -s conductor/src/bootstrap/res/drawable-nodpi/app_icon.png
test -s feature/driver/src/main/res/raw/reserva_calida.wav
test -s feature/driver/src/main/res/drawable-nodpi/conductor_bg_texture.png
! grep -q 'conductor/src/main/java' feature/driver/build.gradle
git add -A conductor/src/main feature/driver/src/main conductor/src/bootstrap feature/driver/build.gradle .github/workflows/build-mapa-mosaic-r25-27.yml
echo "PASS: original driver Java and assets physically moved into feature:driver"
