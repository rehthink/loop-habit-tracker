plugins {
    alias(libs.plugins.loop.jvm.library)
}

dependencies {
    api(projects.core.model)
    api(projects.engine.insights)
}
