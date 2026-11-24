# Kontekst pracy: odtworzenie VibeStudio Automatora

## Cel
Odtworzyć źródła i zbudować nowego Automatora (`dev.vibestudio.automator`), opcjonalny moduł pomocniczy dla VibeStudio IDE. Stare źródła przepadły, zostały: spec `~/vsautomatorspecs.md` i stary APK `~/VibeStudioAutomator (3).apk` (v0.1, minSdk 26, targetSdk 28).

## Decyzje użytkownika
1. `com.vibestudio.dev` (build z `build_dev.sh`) musi być domyślnie na liście dozwolonych pakietów Automatora, żeby IDE mogło kompilować wersje dev i testować je przez Automatora. Dopuszczone jako aliasy też: `com.vibestudio.app`, `com.vibestudio.app.debug`, `com.vibestudio.app`, `com.vibestudio.dev`.
2. Podpis: ten sam `debug.keystore` co IDE (skopiowany z `~/VibeStudioIDE/debug.keystore`). Uprawnienie `dev.vibestudio.permission.BIND_AUTOMATOR` ma `protectionLevel=signature`, więc klucz musi być wspólny.
3. System budowania: fork `build.sh` z IDE (Termux: `aapt2`, `javac`, `d8`, `apksigner`, bez Gradle). Gradle/Compose tylko awaryjnie.
4. **Język: Java, nie Kotlin** (spójność z IDE). UI na zwykłych Views, kolory VibeStudio: tło `#121212`, akcent `#1ECFBA`.
5. Shizuku przez Maven: dozwolone.
6. Osobne repo git w `~/VibeStudioAutomator`. Zakres: cała specyfikacja, ale część można odłożyć.

## Stan środowiska
- IDE: `~/VibeStudioIDE`, branch `0.2.1`. Debug build: `./build_dev.sh` → `bin/VibeStudio-Dev.apk` (działa).
- Zainstalowane pakiety Termux: openjdk-17, aapt, aapt2, apksigner, d8, maven, kotlin itd. Nie ma `termux-shizuku-tools`.
- Wcześniej `aapt2` nie działał na złym branchu. Na `0.2.1` build przeszedł. `build.sh` ma fallback do legacy `aapt`.
- Przed `aapt`/`aapt2` w sesji ustawić `export ANDROID_DATA=/data`.
- Wyjście terminala bywa opóźnione. Przy niepewności użyć `terminal_wait` z wzorcem lub `read_terminal_output`.

## Co jest zrobione w `~/VibeStudioAutomator`
- `git init`
- Struktura: `src/main/java/dev/vibestudio/automator/{ipc,driver,hud,ui}`, `src/main/res/{values,drawable,xml}`, `libs/`
- Skopiowane z IDE: `ipc/IAutomatorService.java`, `IAutomatorCallback.java`, `IAutomatorLogListener.java` (ręcznie pisane Binder Stub/Proxy, to jest wiążący kontrakt), `debug.keystore`, `libs/android.jar`
- `pom.xml`: zależności `dev.rikka.shizuku` api, provider, aidl, shared 13.1.5 jako `aar` z wykluczeniem tranzytywnych. Pobrane do `libs/deps/*.aar` (`mvn -U dependency:copy-dependencies -DoutputDirectory=libs/deps -DincludeScope=compile`).

## Czego jeszcze NIE ma
Manifest, zasoby, `build.sh`, cały kod Javy poza `ipc/`, README, commit.

## Kontrakt IPC (po stronie IDE, nie zmieniać)
- Akcja bind: `dev.vibestudio.automator.ACTION_BIND`, pakiet `dev.vibestudio.automator`.
- `IAutomatorService`: transakcje od `FIRST_CALL_TRANSACTION`: getDriverStatus, executeTool(tool, argsJson, callback), emergencyStop, pause, resume, getState, registerLogListener, unregisterLogListener, getLogs.
- `IAutomatorCallback` (oneway): `onSuccess(String)`, `onError(int, String)`. Kody błędów: 10 = użytkownik wstrzymał, 15 = użytkownik zatrzymał.
- `IAutomatorLogListener` (oneway): `onLog(String entryJson)`.
- Nazwy narzędzi wysyłane przez IDE (bez prefiksu `android_`): `dump_ui_tree` (`compact`), `tap_node` (`id`), `tap` (`x`,`y`,`scaleFactor`), `swipe` (`x1,y1,x2,y2,durationMs`), `input_text` (`text`, opcjonalnie `id`), `global_action` (`action`: BACK/HOME/RECENTS/NOTIFICATIONS), `launch_app` (`package`), `screenshot`, `logcat`, `force_stop` (`package`), `shell_install` (`path`). Status i emergency stop idą przez metody service. Spec używa innych nazw (`tap_coordinates`, `capture_screenshot`, `nodeId`). Wiążący jest kod IDE; można dodać aliasy ze speca.
- `android_status` w IDE składa JSON z `getDriverStatus()` plus `state` z `getState()`.
- Opis dla agenta: `VibeStudioIDE/app/src/main/assets/ai/android_tool.md`.

## Manifest starego APK (do odtworzenia)
- package `dev.vibestudio.automator`, versionName 0.1, minSdk 26, targetSdk 28, allowBackup=false
- `<permission BIND_AUTOMATOR protectionLevel=signature>` + uses-permission tego samego, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`
- `<queries>`: intent MAIN, pakiety IDE (`com.vibestudio.app`, `com.vibestudio.dev`), `moe.shizuku.privileged.api`
- Application `.AutomatorApp`
- Activities: `.CrashActivity` (`:crash_process`, nieeksportowana, excludeFromRecents), `.ui.MainActivity` (LAUNCHER), `.ui.AgentBubbleActivity` (taskAffinity="", allowEmbedded, documentLaunchMode=2, resizeable)
- Services: `.driver.VibeAccessibilityService` (BIND_ACCESSIBILITY_SERVICE, `@xml/accessibility_config`: canRetrieveWindowContent, canPerformGestures, canTakeScreenshot, eventTypes 0x400020, flags 0x53, timeout 100), `.ipc.AutomatorBoundService` (permission BIND_AUTOMATOR, action ACTION_BIND)
- Receiver `.hud.BubbleActionReceiver` (nieeksportowany)
- Provider `rikka.shizuku.ShizukuProvider`, authorities `dev.vibestudio.automator.shizuku`

## Plan dalszy
1. Manifest, zasoby (motyw ciemny, `#121212` / `#1ECFBA`, ikona), `accessibility_config.xml`.
2. Klasy: `AutomatorApp`, `CallerVerifier` (UID → pakiet z allowlisty + własny UID, opcjonalnie sprawdzenie certyfikatu), `ScopeGuard` + allowlista (domyślnie pakiety VibeStudio + `com.vibestudio.dev` + instalator pakietów), `AutomatorBoundService`, `VibeAccessibilityService`, drivery Accessibility i Shizuku z fallbackiem, `ScreenCompressor` (limit 64 KB), `AgentHudOverlay` (ramka RGB + pigułka STOP), `SelfHostWatchdog` (crash/ANR → powrót do IDE), `BubbleActionReceiver`, `AgentBubbleActivity`, `CrashActivity`, `MainActivity` (karta Accessibility, karta Shizuku, wybór pakietów, Full Device Mode, test ramki i zrzutu).
3. `build.sh` na bazie IDE: bez libtermux i Kotlina; `aapt2`/`aapt` → `javac` (`-source 1.8`) → `d8` → podpis `debug.keystore` → `bin/VibeStudioAutomator.apk`. AAR-y Shizuku: wypakować `classes.jar`.
4. Test: zainstalować, włączyć Accessibility, sprawdzić bind z `VibeStudio-Dev.apk` i narzędzia.
5. Commit w nowym repo, README.
