package lyricon

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Android 库模块（protocol 与 common/base/extensions-android）共用的构建约定。
 *
 * 模块脚本只需声明自己的 namespace、KSP（如需要）以及额外依赖。
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

        val projectRef = this

        extensions.configure<LibraryExtension> {
            compileSdk {
                version = release(36)
            }

            defaultConfig {
                minSdk = 27
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                consumerProguardFiles("consumer-rules.pro")
            }

            buildTypes {
                getByName("release") {
                    isMinifyEnabled = false
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro"
                    )
                }
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }

        dependencies {
            add("implementation", projectRef.lib("yukihook-core"))
            add("implementation", projectRef.lib("yukihook-runtime-libxposed"))
            add("implementation", projectRef.lib("yukihook-extension-kavaref"))
            add("implementation", projectRef.lib("kavaref-core"))
            add("implementation", projectRef.lib("kavaref-android"))
            add("implementation", projectRef.lib("kavaref-extension"))
            add("compileOnly", projectRef.lib("libxposed-api"))
            add("compileOnly", projectRef.lib("xposed-api"))
            add("implementation", projectRef.lib("kotlinx-serialization-json"))
            add("implementation", projectRef.lib("androidx-core-ktx"))
            add("testImplementation", projectRef.lib("junit"))
            add("androidTestImplementation", projectRef.lib("androidx-junit"))
            add("androidTestImplementation", projectRef.lib("androidx-espresso-core"))
        }
    }
}
