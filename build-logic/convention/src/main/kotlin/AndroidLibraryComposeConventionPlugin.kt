import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    with(target) {
      with(pluginManager) {
        apply("com.android.library")
      }

      extensions.configure<LibraryExtension> {
        buildFeatures.compose = true
        composeOptions.kotlinCompilerExtensionVersion = "1.4.1-dev-k1.8.10-c312d77f4cb"
      }

      dependencies {
        add("implementation", platform("androidx.compose:compose-bom:2023.01.00"))
        // all compose projects need the runtime.
        // we can switch this to implementation instead of api once a fix is pushed for
        // https://issuetracker.google.com/issues/209688774.
        add("api", "androidx.compose.runtime:runtime")
      }
    }
  }
}
