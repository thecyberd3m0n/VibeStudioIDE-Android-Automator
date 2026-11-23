#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
export ANDROID_DATA=${ANDROID_DATA:-/data} ANDROID_ROOT=${ANDROID_ROOT:-/system}
APK=VibeStudioAutomator.apk
rm -rf build obj gen_r bin; mkdir -p build/aar obj gen_r bin
CP=libs/android.jar
for a in libs/deps/*.aar; do n=$(basename $a .aar); mkdir -p build/aar/$n; unzip -qo $a -d build/aar/$n; CP=$CP:build/aar/$n/classes.jar; done
MAN=src/main/AndroidManifest.xml
if aapt2 version >/dev/null 2>&1; then
  mkdir -p build/compiled
  aapt2 compile --dir src/main/res -o build/compiled/
  aapt2 link -o bin/app.unsigned.apk -I libs/android.jar --manifest $MAN --min-sdk-version 26 --target-sdk-version 28 --version-code 2 --version-name 0.2 --java gen_r --auto-add-overlay build/compiled/*.flat
else
  echo "aapt2 unusable, using aapt"
  aapt package -f -m -J gen_r -M $MAN -I libs/android.jar -S src/main/res -F bin/app.unsigned.apk --min-sdk-version 26 --target-sdk-version 28 --version-code 2 --version-name 0.2 --auto-add-overlay
fi
javac -source 1.8 -target 1.8 -nowarn -cp "$CP" -d obj $(find src/main/java gen_r -name '*.java')
(cd obj && jar cf ../bin/classes.jar .)
LIBS=""; for a in build/aar/*/classes.jar; do LIBS="$LIBS $a"; done
d8 --min-api 26 --lib libs/android.jar --output bin/ bin/classes.jar $LIBS
(cd bin && aapt add app.unsigned.apk classes*.dex >/dev/null)
zipalign -f -p 4 bin/app.unsigned.apk bin/app.aligned.apk
apksigner sign --ks debug.keystore --ks-pass pass:android --out bin/$APK bin/app.aligned.apk
echo "BUILD OK: bin/$APK"
