#!/bin/bash
# Builds Edge Volume without Gradle: aapt2 + javac + d8 + apksigner.
set -euo pipefail
cd "$(dirname "$0")"
SDK=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}
BT=$SDK/build-tools/36.0.0
JAR=$SDK/platforms/android-36/android.jar
rm -rf build && mkdir -p build/res build/classes
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml -o build/unsigned.apk build/res.zip
javac --release 11 -classpath "$JAR" -d build/classes $(find src -name '*.java')
"$BT/d8" --release --lib "$JAR" --min-api 29 --output build $(find build/classes -name '*.class')
(cd build && zip -q unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk
KS=debug.keystore
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
  -alias edgevolume -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Edge Volume" >/dev/null 2>&1
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --ks-key-alias edgevolume \
  --out EdgeVolume.apk build/aligned.apk
echo "OK -> $(pwd)/EdgeVolume.apk"
