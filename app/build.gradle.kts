plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    // Internal Kotlin package stays com.example.iptvapp; the install/store
    // identity below is what the user actually sees.
    namespace = "com.example.iptvapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tribaltv.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.1.0"
    }

    // Release signing comes entirely from env vars (the CI release job
    // decodes the TRIBAL_TV_KEYSTORE_B64 secret into a file and passes
    // these). With none set — local builds, or CI before the keystore
    // secret exists — release simply builds unsigned and nothing else
    // changes.
    val keystoreFile = System.getenv("TRIBAL_TV_KEYSTORE_FILE")
    val keystorePassword = System.getenv("TRIBAL_TV_KEYSTORE_PASSWORD")
    val keyAlias = System.getenv("TRIBAL_TV_KEY_ALIAS")
    val keyPassword = System.getenv("TRIBAL_TV_KEY_PASSWORD")

    signingConfigs {
        if (keystoreFile != null && keystorePassword != null && keyAlias != null && keyPassword != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    }

    testOptions {
        unitTests {
            // Robolectric simulates the Android framework inside the JVM
            // unit-test task and needs the merged resources/assets to do it.
            // Used for the real Room 3 -> 4 migration test.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    // ViewModel + viewModelScope (IptvViewModel). Same lifecycle line.
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    val media3 = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-datasource:$media3")
    // Media session + background play (PlaybackService). Same 1.4.1 line
    // as the rest of media3.
    implementation("androidx.media3:media3-session:$media3")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Channel logos. Coil 3.0.4, NOT latest: 3.6.3 is built with Kotlin
    // 2.4.10 and 3.1.0 with 2.1.10, both unreadable by our Kotlin 2.0.20;
    // 3.0.4 was built with 2.0.21 — the same 2.0.x line.
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

    // JVM unit tests only — nothing from this line lands in the APK.
    testImplementation("junit:junit:4.13.2")

    // Robolectric runs real Android framework code (and real SQLite) inside the
    // JVM unit-test task. That is how the Room 3 -> 4 migration is actually
    // EXECUTED on CI instead of only being inspected as SQL. Pinned to 4.14.1:
    // known-good with AGP 8.6 / compileSdk 35 / JDK 17, and test-only. The
    // first run downloads the android-all jar over the network, so CI gets
    // slower (there is no Gradle cache in this repo's workflow).
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}