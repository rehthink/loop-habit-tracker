plugins {
    alias(libs.plugins.loop.android.feature)
}

dependencies {
    implementation(projects.engine.gamification)
    implementation(libs.vico.compose.m3)
}
