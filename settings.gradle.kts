pluginManagement {
    repositories {
        google() { // This 'google' block might have content {} inside. That's fine.
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()       // <--- THIS IS CRUCIAL
        mavenCentral() // <--- THIS IS CRUCIAL
    }
}

rootProject.name = "SPTASSIST"
include(":app")