/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.credentials

import android.content.Context

/** 7.0.0 及之后。 */
internal class V700Impl : DeveloperTokenReader {
    override fun read(context: Context): String? = DataStoreTokenReader.read(context)
}
