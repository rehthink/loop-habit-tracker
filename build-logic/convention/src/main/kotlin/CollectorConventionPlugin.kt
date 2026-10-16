import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * A :collector:* module: one data source. Depends only on the domain layer (it writes through
 * repositories, never the database) and registers its TrackerSource with Hilt multibinding.
 */
class CollectorConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("loop.android.library")
        pluginManager.apply("loop.hilt")

        dependencies {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:domain"))
            add("implementation", libs.lib("kotlinx-coroutines-android"))
            add("implementation", libs.lib("androidx-core-ktx"))
            add("implementation", libs.lib("androidx-work-runtime-ktx"))
        }
    }
}
