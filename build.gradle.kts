// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false // If you are not using Compose, this line can be removed.
}

// ADD THIS BLOCK START
//allprojects {
//    repositories {
//        google() // Google's Maven repository, essential for play-services-* libraries
//        mavenCentral() // Maven Central repository, for most other common libraries
//        // If you have any other specific repositories for certain libraries (e.g., Jitpack), add them here:
//        // maven { url = uri("https://jitpack.io") }
//    }
//}
// ADD THIS BLOCK END