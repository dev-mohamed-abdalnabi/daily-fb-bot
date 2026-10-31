#!/usr/bin/env bash
# Generates the Android/Gradle project structure from the flat source files in this repo root.
# Safe to re-run: it regenerates app/ and the Gradle files every time.
#
#   ./setup.sh            # generate project (+ Gradle wrapper if missing)
#   ./gradlew assembleDebug testDebugUnitTest
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

GRADLE_VERSION="${GRADLE_VERSION:-8.9}"
APP_ID="com.abdalnaby.dynamicisland"

echo "==> Cleaning previous generated sources"
rm -rf app/src
mkdir -p app/src/main/java app/src/test/java

echo "==> Placing Kotlin sources by package"
shopt -s nullglob
count_main=0; count_test=0
for f in *.kt; do
  pkg="$(grep -m1 -E '^package ' "$f" | awk '{print $2}' | tr -d ';\r')"
  if [[ -z "$pkg" ]]; then echo "!! $f has no package line" >&2; exit 1; fi
  dir="${pkg//./\/}"
  if [[ "$f" == *Test.kt ]]; then
    mkdir -p "app/src/test/java/$dir"; cp "$f" "app/src/test/java/$dir/"; count_test=$((count_test+1))
  else
    mkdir -p "app/src/main/java/$dir"; cp "$f" "app/src/main/java/$dir/"; count_main=$((count_main+1))
  fi
done
echo "    $count_main main files, $count_test test files"
cp AndroidManifest.xml app/src/main/AndroidManifest.xml

echo "==> Writing resources"
RES=app/src/main/res
mkdir -p "$RES/values" "$RES/values-night" "$RES/drawable" "$RES/mipmap-anydpi-v26" "$RES/xml"

cat > "$RES/values/strings.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Dynamic Island</string>
    <string name="listener_label">Dynamic Island notifications</string>
    <string name="share_label">Pin to Island</string>
    <string name="perm_live_activity_label">post Dynamic Island live activities</string>
    <string name="perm_live_activity_desc">Allows the app to show live activities (progress, ETA, scores) on the Dynamic Island.</string>
</resources>
EOF

cat > "$RES/values/colors.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="launcher_bg">#FF000000</color>
    <color name="launcher_fg">#FFFFFFFF</color>
</resources>
EOF

cat > "$RES/values/themes.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.DynamicIsland" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:windowBackground">@android:color/white</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
    <style name="Theme.DynamicIsland.Translucent" parent="android:Theme.Translucent.NoTitleBar">
        <item name="android:windowNoTitle">true</item>
        <item name="android:windowIsTranslucent">true</item>
        <item name="android:windowBackground">@android:color/transparent</item>
        <item name="android:backgroundDimEnabled">false</item>
    </style>
</resources>
EOF

cat > "$RES/values-night/themes.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.DynamicIsland" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">@android:color/black</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
</resources>
EOF

cat > "$RES/drawable/ic_island.xml" <<'EOF'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFFFF"
        android:pathData="M7,8h10a4,4 0,0 1,0 8h-10a4,4 0,0 1,0 -8z"/>
</vector>
EOF

cat > "$RES/drawable/ic_launcher_foreground.xml" <<'EOF'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="@color/launcher_fg"
        android:pathData="M38,44h32a10,10 0,0 1,0 20h-32a10,10 0,0 1,0 -20z"/>
    <path android:fillColor="#FF30D158"
        android:pathData="M64,54m-4,0a4,4 0,1 1,8 0a4,4 0,1 1,-8 0"/>
</vector>
EOF

cat > "$RES/mipmap-anydpi-v26/ic_launcher.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/launcher_bg"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
    <monochrome android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
EOF

echo "==> Writing Gradle build files"
cat > settings.gradle.kts <<'EOF'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "DynamicIsland"
include(":app")
EOF

cat > build.gradle.kts <<'EOF'
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20" apply false
}
EOF

cat > gradle.properties <<'EOF'
org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
org.gradle.caching=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
EOF

cat > app/build.gradle.kts <<EOF
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "$APP_ID"
    compileSdk = 34

    defaultConfig {
        applicationId = "$APP_ID"
        minSdk = 29
        targetSdk = 34
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "1.0.0"
    }

    signingConfigs {
        create("release") {
            val ks = System.getenv("SIGNING_KEYSTORE_PATH")
            if (!ks.isNullOrBlank() && file(ks).exists()) {
                storeFile = file(ks)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Real keystore when provided via env (CI secrets), otherwise the debug key so the APK still installs.
            val release = signingConfigs.getByName("release")
            signingConfig = if (release.storeFile != null) release else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    lint {
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-service:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    testImplementation("junit:junit:4.13.2")
}
EOF

cat > app/proguard-rules.pro <<'EOF'
# Minification is disabled; kept for when it is enabled.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.abdalnaby.dynamicisland.**$$serializer { *; }
-keepclassmembers class com.abdalnaby.dynamicisland.** { *** Companion; kotlinx.serialization.KSerializer serializer(...); }
EOF

if [[ -n "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]]; then
  echo "sdk.dir=${ANDROID_HOME:-$ANDROID_SDK_ROOT}" > local.properties
fi

echo "==> Gradle wrapper"
if [[ -x gradlew && -f gradle/wrapper/gradle-wrapper.jar ]]; then
  echo "    wrapper already present"
else
  if command -v gradle >/dev/null 2>&1; then
    GRADLE_BIN="gradle"
  else
    CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/dynamic-island-bootstrap"
    mkdir -p "$CACHE"
    if [[ ! -x "$CACHE/gradle-$GRADLE_VERSION/bin/gradle" ]]; then
      echo "    downloading Gradle $GRADLE_VERSION to bootstrap the wrapper"
      curl -fsSL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$CACHE/gradle.zip"
      unzip -q -o "$CACHE/gradle.zip" -d "$CACHE"
    fi
    GRADLE_BIN="$CACHE/gradle-$GRADLE_VERSION/bin/gradle"
  fi
  # Generate in an empty scratch project so this doesn't need the Android SDK or plugin downloads.
  TMP_WRAPPER="$(mktemp -d)"
  touch "$TMP_WRAPPER/settings.gradle.kts"
  (cd "$TMP_WRAPPER" && "$GRADLE_BIN" wrapper --gradle-version "$GRADLE_VERSION" --distribution-type bin --no-daemon -q)
  mkdir -p gradle/wrapper
  cp "$TMP_WRAPPER/gradlew" "$TMP_WRAPPER/gradlew.bat" .
  cp "$TMP_WRAPPER/gradle/wrapper/gradle-wrapper.jar" "$TMP_WRAPPER/gradle/wrapper/gradle-wrapper.properties" gradle/wrapper/
  rm -rf "$TMP_WRAPPER"
  chmod +x gradlew
fi

echo "==> Done. Next: ./gradlew testDebugUnitTest assembleDebug"
