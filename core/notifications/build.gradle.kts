plugins {
    alias(libs.plugins.loop.android.library)
    alias(libs.plugins.loop.hilt)
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.engine.gamification)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
