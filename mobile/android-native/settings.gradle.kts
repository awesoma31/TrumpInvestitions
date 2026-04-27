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

rootProject.name = "TrumpInvestitions"

include(":app")
include(":core")
include(":data")
include(":domain")
include(":ui")
include(":navigation")
include(":feature:auth")
include(":feature:trading")
include(":feature:portfolio")
include(":feature:charts")
include(":feature:settings")