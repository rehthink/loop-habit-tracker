plugins {
    alias(libs.plugins.loop.android.library)
    alias(libs.plugins.loop.android.room)
    alias(libs.plugins.loop.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    sourceSets {
        // Lets MigrationTestHelper read the exported schemas.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.serialization.json)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.turbine)
}
