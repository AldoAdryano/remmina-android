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

rootProject.name = "RemoteX-Android"
include(
    ":app",
    ":core:model",
    ":core:database",
    ":core:security",
    ":core:logging",
    ":core:ui",
    ":feature:home",
    ":feature:connections",
    ":feature:vnc",
    ":feature:ssh",
    ":feature:sftp",
    ":feature:settings",
)
