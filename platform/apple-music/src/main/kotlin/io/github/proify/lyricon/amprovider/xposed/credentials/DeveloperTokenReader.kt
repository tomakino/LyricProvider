/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.credentials

import android.content.Context

/** 读取 developerToken。 */
internal interface DeveloperTokenReader {
    fun read(context: Context): String?
}

/** 按版本选择实现。 */
internal object DeveloperTokenReaders {
    fun create(versionCode: Int): DeveloperTokenReader = when {
        versionCode <= VERSION_512 -> V512Impl()
        versionCode < VERSION_700 -> V652Impl()
        else -> V700Impl()
    }

    private const val VERSION_512 = 1518
    private const val VERSION_700 = 1614
}
