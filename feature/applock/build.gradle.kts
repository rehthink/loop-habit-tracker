plugins {
    alias(libs.plugins.loop.android.feature)
}

dependencies {
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
}
