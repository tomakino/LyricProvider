/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.credentials

import android.content.Context

/** 通过宿主 AppSharedPreferences.getApiToken 读取明文 token。 */
internal object LegacyTokenBridge {

    private const val APP_SHARED_PREFERENCES =
        "com.apple.android.music.utils.AppSharedPreferences"
    private const val METHOD_GET_API_TOKEN = "getApiToken"
    private const val TOKEN_KEY = "musicAndroid"

    fun read(context: Context): String? = runCatching {
        val cls = Class.forName(APP_SHARED_PREFERENCES, false, context.classLoader)
        val method = cls.getMethod(METHOD_GET_API_TOKEN, String::class.java)
        method.invoke(null, TOKEN_KEY) as? String
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
