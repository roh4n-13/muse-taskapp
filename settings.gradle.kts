pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // Resolve plugin markers directly from Maven repos (the plugin portal
    // is unreachable in some sandboxed networks). requested.version is null
    // when a submodule applies a plugin without a version, so fall back to
    // the known versions.
    resolutionStrategy {
        eachPlugin {
            val v = requested.version
            when (requested.id.id) {
                "com.android.application" ->
                    useModule("com.android.tools.build:gradle:${v ?: "8.5.2"}")
                "org.jetbrains.kotlin.android" ->
                    useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${v ?: "1.9.24"}")
                "com.google.devtools.ksp" ->
                    useModule("com.google.devtools.ksp:symbol-processing-gradle-plugin:${v ?: "1.9.24-1.0.20"}")
            }
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "TaskTrackerPro"
include(":app")
