plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.felipeftn.magnusorgue"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.felipeftn.magnusorgue"
        minSdk = 26 // AAudio needs 26; MidiManager only needs 23, so this is the real floor
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        externalNativeBuild {
            cmake {
                // Oboe's prebuilt (prefab) libs link against the shared STL,
                // so we must too — mixing STLs is a hard error.
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }

    androidResources {
        // The rank packs are raw PCM; deflate saves almost nothing and
        // uncompressed assets can be read straight off the APK.
        noCompress += "mrk"
    }

    buildFeatures {
        compose = true
        buildConfig = true  // version name in the About drawer
        // Prefab unpacks Oboe's headers + .so straight from the AAR,
        // so CMake can just find_package() it. No git submodule nonsense.
        prefab = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            // App is tiny; minification buys nothing and JNI + R8 is a
            // classic footgun. Not worth it.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")

    // Low-latency audio. https://github.com/google/oboe
    implementation("com.google.oboe:oboe:1.9.3")

    testImplementation("junit:junit:4.13.2")
}
