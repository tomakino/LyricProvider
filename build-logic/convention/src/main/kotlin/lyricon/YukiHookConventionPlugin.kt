package lyricon

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * 叠加 YukiHookAPI + KavaRef + Xposed API 依赖（并启用 KSP 处理）。
 */
class YukiHookConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("com.google.devtools.ksp")

        val projectRef = this

        dependencies {
            add("implementation", projectRef.lib("yukihookapi-api"))
            add("implementation", projectRef.lib("kavaref-core"))
            add("implementation", projectRef.lib("kavaref-extension"))
            add("compileOnly", projectRef.lib("xposed-api"))
            add("ksp", projectRef.lib("yukihookapi-ksp-xposed"))
        }
    }
}
