// app/build.gradle.kts (Module :app)
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.proactive.sptassist"
    compileSdk = 35 // Keep your compileSdk as 35

    defaultConfig {
        applicationId = "com.proactive.sptassist"
        minSdk = 24
        targetSdk = 34 // You might want to update this to 35 to match compileSdk for consistency
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildToolsVersion = "35.0.0"

    // IMPORTANT: Add this block for Compose if you haven't already
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1" // Use a version compatible with your Kotlin version (e.g., 1.5.1 for Kotlin 1.9.0)
    }
}

dependencies {
    // ... existing dependencies

    // For core library desugaring (allows using Java 8+ APIs on older Android versions)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    // OkHttp for WebSocket
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Kotlin Coroutines for asynchronous operations and structured concurrency
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Google Play Services for Location (FusedLocationProviderClient)
    implementation("com.google.android.gms:play-services-location:21.0.1")
//    implementation('androidx.lifecycle:lifecycle-service:2.6.1'
    // Existing dependencies from your project:
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // === ADD THESE JETPACK COMPOSE DEPENDENCIES ===
    // Use the latest stable Compose BOM for consistent versions
    // As of 2024-06-04, a newer BOM like 2024.04.00 or 2024.05.00 might be available.
    // Let's use 2024.04.00 for now. You can check the official documentation for the absolute latest.
    implementation(platform("androidx.compose:compose-bom:2024.04.00")) // Using a very recent BOM
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3") // If you're using Material 3 components
    // implementation("androidx.compose.material:material") // If you're using Material 2 components

    // Activity Compose for using Compose in Activities
    implementation("androidx.activity:activity-compose:1.9.0") // Match with other androidx versions if possible

    // For unit tests
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // For Compose UI testing
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.04.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}