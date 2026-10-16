plugins {
    alias(libs.plugins.loop.android.application)
    alias(libs.plugins.loop.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.loop"

    defaultConfig {
        applicationId = "app.loop.habits"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)

    implementation(projects.core.network)
    implementation(projects.core.notifications)
    implementation(projects.engine.insights)
    implementation(projects.engine.gamification)

    implementation(projects.collector.notifications)
    implementation(projects.collector.screentime)
    implementation(projects.collector.steps)
    implementation(projects.collector.activity)
    implementation(projects.collector.healthconnect)
    implementation(projects.collector.geofence)
    implementation(projects.collector.sleep)
    implementation(projects.collector.intake)
    implementation(projects.collector.checkins)

    implementation(projects.feature.onboarding)
    implementation(projects.feature.today)
    implementation(projects.feature.progress)
    implementation(projects.feature.me)
    implementation(projects.feature.permissions)
    implementation(projects.feature.applock)
    implementation(projects.feature.screentime)
    implementation(projects.feature.movement)
    implementation(projects.feature.sleep)
    implementation(projects.feature.food)
    implementation(projects.feature.checkins)
    implementation(projects.feature.notifications)
    implementation(projects.feature.widgets)
    implementation(projects.feature.cycle)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
