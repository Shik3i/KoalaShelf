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

rootProject.name = "KoalaShelf"

include(":app")
include(":core:designsystem")
include(":core:network")
include(":core:data")
include(":core:player")
include(":feature:auth")
include(":feature:home")
include(":feature:library")
include(":feature:player")
