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

rootProject.name = "dmap"

include(":shared")
include(":androidApp")

// iosApp is not a Gradle module. It is an Xcode project that consumes
// :shared as an XCFramework via SPM. See iosApp/README.md.
