/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.credentials

import android.content.Context
import com.highcapable.kavaref.extension.classOf
import com.highcapable.kavaref.extension.toClass

internal class LegacyDeveloperTokenReader : DeveloperTokenReader {

    companion object {
        private const val APP_SHARED_PREFERENCES =
            "com.apple.android.music.utils.AppSharedPreferences"
        private const val METHOD_GET_API_TOKEN = "getApiToken"
        private const val TOKEN_KEY = "musicAndroid"
    }

    override fun read(context: Context): String? = runCatching {
        val cls = APP_SHARED_PREFERENCES.toClass(context.classLoader, false)
        val method = cls.getMethod(METHOD_GET_API_TOKEN, classOf<String>())
        method.invoke(null, TOKEN_KEY) as? String
    }.getOrNull()?.takeIf { it.isNotBlank() }

}