plugins {
    alias(libs.plugins.loop.android.feature)
}

dependencies {
    implementation(libs.androidx.biometric)
}

dependencies {
    implementation(projects.engine.gamification)
}
