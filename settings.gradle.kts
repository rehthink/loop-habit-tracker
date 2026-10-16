pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "Loop"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

include(":core:model")
include(":core:domain")
include(":core:database")
include(":core:datastore")
include(":core:data")
include(":core:designsystem")
include(":core:ui")
include(":core:network")
include(":core:notifications")

include(":engine:insights")
include(":engine:gamification")

include(":collector:notifications")
include(":collector:screentime")
include(":collector:steps")
include(":collector:activity")
include(":collector:healthconnect")
include(":collector:geofence")
include(":collector:sleep")
include(":collector:intake")
include(":collector:checkins")

include(":feature:onboarding")
include(":feature:today")
include(":feature:progress")
include(":feature:me")
include(":feature:permissions")
include(":feature:applock")
include(":feature:screentime")
include(":feature:movement")
include(":feature:sleep")
include(":feature:food")
include(":feature:checkins")
include(":feature:notifications")
include(":feature:widgets")
include(":feature:cycle")
