package lyricon

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * 所有可安装的 provider APK 模块共用的构建约定（构建配置 + 基础依赖）。
 *
 * 具体使用哪种 hook 框架由模块叠加 lyricon.yukihook 或 lyricon.libxposed 决定。
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

        val compileSdkVersion = extraInt("compileSdkVersion", 37)
        val targetSdkVersion = extraInt("targetSdkVersion", 37)
        val projectRef = this

        extensions.configure<ApplicationExtension> {
            compileSdk {
                version = release(compileSdkVersion)
            }

            defaultConfig {
                minSdk = 27
                targetSdk = targetSdkVersion
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            signingConfigs {
                create("release") {
                    storeFile = System.getenv("RELEASE_STORE_FILE")?.let { java.io.File(it) } ?: rootProject.file("release.jks")
                    storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                    keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                    keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
                }
            }

            buildTypes {
                getByName("release") {
                    signingConfig = signingConfigs.getByName("release")
                    isMinifyEnabled = true
                    isShrinkResources = true
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

            buildFeatures {
                buildConfig = true
            }
        }

        dependencies {
            add("implementation", projectRef.lib("lyricon-provider"))
            add("implementation", projectRef.lib("kotlinx-serialization-json"))
            add("implementation", projectRef.lib("androidx-core-ktx"))
            add("testImplementation", projectRef.lib("junit"))
            add("androidTestImplementation", projectRef.lib("androidx-junit"))
            add("androidTestImplementation", projectRef.lib("androidx-espresso-core"))
        }
    }
}
