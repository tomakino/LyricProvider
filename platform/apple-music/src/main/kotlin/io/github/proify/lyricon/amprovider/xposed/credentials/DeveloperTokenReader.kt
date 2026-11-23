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
    fun create(versionCode: Long): DeveloperTokenReader = when {
        versionCode < VERSION_700 -> LegacyDeveloperTokenReader()
        else -> DataStoreDeveloperTokenReader()
    }

    private const val VERSION_700 = 1614
}
