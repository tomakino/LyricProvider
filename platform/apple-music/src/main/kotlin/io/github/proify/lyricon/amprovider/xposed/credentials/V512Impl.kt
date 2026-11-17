/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.credentials

import android.content.Context

/** 5.1.2 及更早。 */
internal class V512Impl : DeveloperTokenReader {
    override fun read(context: Context): String? = LegacyTokenBridge.read(context)
}
