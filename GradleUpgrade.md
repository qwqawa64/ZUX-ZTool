# Gradle/AGP Upgrade Plan

This document is an agent-readable plan for upgrading ZUX-ZTool from Gradle 8/AGP 8 to Gradle 9/AGP 9, with the goal of unblocking `compileSdk = 37` and newer Miuix releases/features.

## Current Baseline

- Project: single Android application module `:app`.
- Gradle Wrapper: `8.14.3`, currently using `https://mirrors.aliyun.com/gradle/distributions/v8.14.3/gradle-8.14.3-bin.zip`.
- AGP: `8.13.0` in `gradle/libs.versions.toml`.
- Kotlin: `2.3.21`.
- Compose BOM: `2025.10.00`.
- Miuix baseline before this upgrade: `0.8.8`, dependency alias `miuix-android` -> `top.yukonga.miuix.kmp:miuix-android`.
- Android SDK: `compileSdk = 36`, `targetSdk = 36`, `minSdk = 27`.
- JVM target: Java/Kotlin `11`.
- Validation command required by project instructions: `.\gradlew.bat assembleDebug`.

## External Version Facts To Recheck Before Editing

Recheck these immediately before implementation because Gradle, AGP, Android SDK, and Miuix versions change.

- Gradle current stable: use the latest stable Gradle 9.x from https://gradle.org/releases/ and https://docs.gradle.org/current/release-notes.html. As of 2026-06-08, this upgrade uses `9.5.1`.
- AGP current stable: use the latest stable AGP 9.x from https://developer.android.com/build/releases/gradle-plugin and https://developer.android.com/build/releases/about-agp. As of 2026-06-08, this upgrade uses `9.1.1` because it is the stable AGP version that explicitly supports API 37 in Android's compatibility table.
- AGP/Gradle compatibility: Android docs state AGP `9.2` requires Gradle `9.4.1` minimum; AGP `9.1` requires Gradle `9.3.1`; AGP `9.0` requires Gradle `9.1.0`.
- API 37 compatibility: Android docs state API level `37` requires Android Studio `Panda 3 | 2025.3.3 Patch 1` minimum and AGP `9.1.1` minimum.
- Important discrepancy to verify during implementation: AGP `9.2.0` release notes may still state max supported API level `36.1`, while the AGP overview compatibility table states API level `37` requires AGP `9.1.1`. Treat `compileSdk = 37` as a build verification gate, not an assumed success.
- Miuix docs source: only use https://compose-miuix-ui.github.io/miuix/dokka/index.html for Miuix API documentation. Do not inspect local downloaded Miuix artifacts for docs.
- Miuix Maven metadata: as of 2026-06-08, Maven Central metadata for `top.yukonga.miuix.kmp:miuix-ui-android` reports latest/release `0.9.2`.

## Target Versions

Primary target:

- Gradle Wrapper: `9.5.1`, using the Aliyun Gradle distribution mirror for mainland China availability.
- AGP: `9.1.1`. Do not move to AGP `9.2.0` for this project until the release notes and a local build both confirm API 37 support.
- Kotlin: keep `2.3.21` initially unless AGP, Compose compiler, or Miuix requires a newer stable Kotlin.
- Compose compiler plugin: keep aligned with Kotlin by keeping `org.jetbrains.kotlin.plugin.compose` on the same Kotlin version.
- Android SDK: move `compileSdk` to `37` and `targetSdk` to `37`.
- Miuix: switch from legacy `top.yukonga.miuix.kmp:miuix-android:0.8.8` to `top.yukonga.miuix.kmp:miuix-ui-android:0.9.2`.
- JDK used by Gradle daemon: JDK `17` minimum. Keep Java/Kotlin compilation target at `11` unless there is a specific reason to raise app bytecode level.

## Files Expected To Change

- `gradle/wrapper/gradle-wrapper.properties`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradlew`
- `gradlew.bat`
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- Potentially `settings.gradle.kts` only if repository/plugin resolution must change.
- Potentially CI/IDE documentation if this repo has CI files added later.

Do not modify these unless the upgrade specifically requires it:

- `assets/xposed_init`
- Xposed metadata in `AndroidManifest.xml`
- `res/values/array.xml` `xposed_scope`
- Existing SharedPreferences/config keys
- Magisk embedding assets and shell scripts

## Pre-Flight Checklist

1. Run `git status --short`.
2. Identify unrelated user changes. Do not revert or format unrelated files.
3. Confirm installed JDK:
   - `.\gradlew.bat --version`
   - `java -version`
4. Ensure Android SDK platform 37 is installed or install it through Android Studio/SDK Manager.
5. Confirm latest versions from official sources listed above.
6. Confirm Miuix latest stable from Maven metadata and read usage docs only from the Miuix Dokka site.

## Implementation Steps

### 1. Update Gradle Wrapper

Preferred command:

```powershell
.\gradlew.bat wrapper --gradle-version 9.5.1 --distribution-type bin
.\gradlew.bat wrapper
```

If the first command fails because current AGP/Gradle compatibility blocks wrapper execution, manually edit `gradle/wrapper/gradle-wrapper.properties` first:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.5.1-bin.zip
```

If this project intentionally stays on the Aliyun Gradle mirror, use the mirror equivalent only after verifying the URL exists:

```properties
distributionUrl=https\://mirrors.aliyun.com/gradle/distributions/v9.5.1/gradle-9.5.1-bin.zip
```

Then run:

```powershell
.\gradlew.bat wrapper
.\gradlew.bat --version
```

Expected result: Gradle reports `9.5.1` or the selected latest stable Gradle 9.x.

### 2. Update Version Catalog

Edit `gradle/libs.versions.toml`:

```toml
agp = "9.1.1"
kotlin = "2.3.21"
miuix = "0.9.2"
```

Use newer stable values if official sources show newer stable versions at implementation time.

Do not use dynamic versions such as `9.2.+`, `latest.release`, or `+`.

### 3. Adjust Android SDK Versions

Edit `app/build.gradle.kts`:

```kotlin
android {
    compileSdk = 37

    defaultConfig {
        targetSdk = 37
    }
}
```

If the upgrade goal is only to consume API 37 libraries/Miuix while avoiding Android 16/17 target behavior changes, use this staged form instead:

```kotlin
android {
    compileSdk = 37

    defaultConfig {
        targetSdk = 36
    }
}
```

Record the choice in the commit message or PR description.

### 4. Replace Internal AGP Variant API

`app/build.gradle.kts` imports and casts to internal AGP API:

```kotlin
import com.android.build.gradle.internal.api.BaseVariantOutputImpl
```

This is not compatible with the AGP 9 upgrade path and must be removed.

Current behavior to preserve:

- Only release APK names are customized.
- Name format: `ZTool_${safeVersionName}_c${versionCode}.apk`
- `/` in `versionName` is replaced with `_`.

AGP `9.1.1` does not expose `outputFileName` on the public Kotlin DSL used by this project. Preserve the release filename contract with a separate release distribution copy task:

```kotlin
tasks.register<Copy>("copyReleaseDistribution") {
    dependsOn("assembleRelease")
    from(releaseOutputDir) {
        include("*.apk")
        include("output-metadata.json")
    }
    from(releaseOutputDir.map { it.dir("baselineProfiles") }) {
        into("baselineProfiles")
    }
    into(layout.projectDirectory.dir("release"))
}
```

The default AGP output remains under `app/build/outputs/apk/release/`. The release distribution task publishes the renamed APK, rewritten `output-metadata.json`, and renamed baseline profile `.dm` files under `app/release/`.

### 5. Resolve AGP 9/Kotlin Behavior Changes

AGP 9 includes built-in Kotlin support and rejects `org.jetbrains.kotlin.android`. Keep only the Compose compiler plugin:

```kotlin
alias(libs.plugins.compose.compiler)
```

Remove the `kotlin-android` plugin alias from `gradle/libs.versions.toml`, root `build.gradle.kts`, and `app/build.gradle.kts`.

Keep `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }` unless Kotlin/AGP validation requires a syntax update.

### 6. Sync And Build

Run in this order:

```powershell
.\gradlew.bat --version
.\gradlew.bat help
.\gradlew.bat assembleDebug
```

If `assembleDebug` passes, also run:

```powershell
.\gradlew.bat assembleRelease copyReleaseDistribution
```

Release build matters because this project has custom release output naming and minification/resource shrinking enabled only for release.

### 7. Fix Compile Errors Conservatively

Handle errors in this order:

1. Gradle/AGP/Kotlin DSL errors in build scripts.
2. Missing Android SDK 37 platform/build tools.
3. AGP internal API breakage around release APK naming.
4. Dependency metadata errors requiring higher AGP/Kotlin/compileSdk.
5. Source compile errors caused by API changes.
6. R8/resource shrinker errors in release only.

Do not refactor app UI, Hook logic, repositories, Xposed metadata, or Magisk assets as part of this upgrade unless a build error directly requires it.

## Validation Gates

The upgrade is complete only when these pass or are explicitly documented as blocked:

- `.\gradlew.bat --version` reports target Gradle 9.x and JDK 17+ daemon.
- `.\gradlew.bat help` completes.
- `.\gradlew.bat assembleDebug` completes.
- `.\gradlew.bat assembleRelease` completes or any release-only failure is documented with exact error text.
- Release distribution files exist under `app/release/`: renamed APK, `output-metadata.json`, and `baselineProfiles/**`, all following the `ZTool_${safeVersionName}_c${versionCode}` file naming contract.
- `compileSdk = 37` is accepted by AGP and SDK tooling, or the exact AGP/API 37 blocker is documented with source links.
- Miuix dependency resolves at the selected latest stable version.

## Rollback Plan

If AGP 9 blocks the project and cannot be fixed in a narrow build-script patch:

1. Keep a separate branch or commit for the attempted upgrade.
2. Revert only files changed by the upgrade attempt.
3. Restore:
   - Gradle Wrapper `8.14.3`
   - AGP `8.13.0`
   - `compileSdk = 36`
   - `targetSdk = 36`
   - previous Miuix version
4. Preserve a failure note in `GradleUpgrade.md` or the PR with:
   - selected Gradle/AGP/Kotlin/Miuix versions
   - exact command that failed
   - first actionable error block
   - whether failure is API 37 support, AGP Variant API, Kotlin, dependency metadata, or R8.

## Suggested Commit Sequence

Use small commits so failures are easy to isolate:

1. `docs: add Gradle 9 upgrade plan`
2. `build: upgrade Gradle wrapper to 9.x`
3. `build: upgrade AGP and Android SDK versions`
4. `build: update miuix dependency`
5. `build: migrate release APK naming for AGP 9` if needed

For this repository, project instructions require committing completed requested tasks unless the user explicitly asks not to commit.
