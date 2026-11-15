/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.cmprovider.xposed

internal object HookTarget {
    private val supportedPackages = setOf(
        "com.netease.cloudmusic",
        "com.hihonor.cloudmusic"
    )

    fun accepts(
        packageName: String,
        applicationPackageName: String,
        processName: String,
        isFirstPackage: Boolean
    ): Boolean = isFirstPackage &&
            packageName == applicationPackageName &&
            packageName in supportedPackages &&
            (processName == packageName || processName == "$packageName:play")
}

internal object CurrentTrackPolicy {
    fun shouldPublish(resultId: Long, currentId: Long?): Boolean = resultId == currentId
}

internal object CapabilitySelection {
    fun <T> uniqueOrNull(candidates: Iterable<T>): T? {
        val iterator = candidates.iterator()
        if (!iterator.hasNext()) return null
        val candidate = iterator.next()
        return candidate.takeUnless { iterator.hasNext() }
    }
}
