package lyricon

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * 叠加 LSPosed Modern API（libxposed）依赖，用于不走 YukiHookAPI 的模块。
 */
class LibXposedConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        val projectRef = this

        dependencies {
            add("compileOnly", projectRef.lib("libxposed-api"))
        }
    }
}
