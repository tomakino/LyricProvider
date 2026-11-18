package lyricon

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * 叠加 YukiHook 1.5.x 依赖（core + runtime-libxposed + KavaRef 扩展）并启用 KSP 处理。
 */
class YukiHookConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("com.google.devtools.ksp")

        val projectRef = this

        dependencies {
            add("implementation", projectRef.lib("yukihook-core"))
            add("implementation", projectRef.lib("yukihook-runtime-libxposed"))
            add("implementation", projectRef.lib("yukihook-extension-kavaref"))
            add("implementation", projectRef.lib("kavaref-core"))
            add("implementation", projectRef.lib("kavaref-android"))
            add("implementation", projectRef.lib("kavaref-extension"))
            add("compileOnly", projectRef.lib("libxposed-api"))
            add("ksp", projectRef.lib("yukihook-compiler"))
        }
    }
}
