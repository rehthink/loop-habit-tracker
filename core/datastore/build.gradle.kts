plugins {
    alias(libs.plugins.loop.android.library)
    alias(libs.plugins.loop.hilt)
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.domain)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
