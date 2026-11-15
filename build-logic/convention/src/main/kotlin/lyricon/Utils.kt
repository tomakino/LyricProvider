package lyricon

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.getByType

internal fun Project.catalog(): VersionCatalog =
    extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun Project.lib(alias: String): Provider<MinimalExternalModuleDependency> {
    val found = catalog().findLibrary(alias)
    if (found.isEmpty) {
        throw IllegalStateException("build-logic: version catalog 中缺少库别名 '" + alias + "'")
    }
    return found.get()
}

internal fun Project.extraInt(key: String, fallback: Int): Int {
    val extra = rootProject.extra
    return if (extra.has(key)) (extra[key] as? Int) ?: fallback else fallback
}
