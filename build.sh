#!/bin/sh
# Build without Gradle (Debian/Ubuntu):
#   sudo apt install aapt apksigner dalvik-exchange android-sdk-platform-23 zipalign
#
# Signing: uses the keystore at $KEYSTORE (default: recomp.keystore).
# If it doesn't exist, a new one is generated. Keep that file private and reuse it:
# Android only installs an update if it's signed with the same key.
# Never commit it (it's in .gitignore).
set -e
AJAR=/usr/lib/android-sdk/platforms/android-23/android.jar
KEYSTORE=${KEYSTORE:-recomp.keystore}
KS_PASS=${KS_PASS:-recomprecomp}
KS_ALIAS=${KS_ALIAS:-recomp}

rm -rf build && mkdir -p build/gen build/obj
aapt package -f -m -J build/gen -M AndroidManifest.xml -S res -A assets -I $AJAR -F build/unsigned.apk
javac -nowarn -source 8 -target 8 -encoding UTF-8 -bootclasspath $AJAR -d build/obj $(find src build/gen -name '*.java') 2>&1 | grep -v -e Picked -e warning || true
dalvik-exchange --dex --min-sdk-version=23 --output=build/classes.dex build/obj
(cd build && aapt add unsigned.apk classes.dex >/dev/null)
zipalign -f 4 build/unsigned.apk build/aligned.apk
[ -f "$KEYSTORE" ] || keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KS_PASS" \
  -alias "$KS_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Recompresseur" 2>&1 | grep -v Picked || true
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --ks-key-alias "$KS_ALIAS" --out build/Recompresseur.apk build/aligned.apk
echo "OK -> build/Recompresseur.apk"
