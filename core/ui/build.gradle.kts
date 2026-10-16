plugins {
    alias(libs.plugins.loop.android.library.compose)
}

dependencies {
    api(projects.core.designsystem)
    implementation(projects.core.model)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.health.connect)
}
