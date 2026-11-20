/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.kgprovider.xposed

import com.highcapable.kavaref.extension.classOf
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam
import io.github.proify.extensions.toRichLyricLines
import io.github.proify.lrckit.LrcParser
import io.github.proify.lyricon.krckit.decrypt.KrcDecryptor
import io.github.proify.lyricon.krckit.parser.KrcParser
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.dexkit.DexKitSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.lang.reflect.Method

internal class LyricFileHooks(
    private val param: PackageParam,
    private val dexkit: DexKitSession,
    private val scope: CoroutineScope,
    private val onLyricsLoaded: (List<RichLyricLine>) -> Unit,
) {

    fun install() {
        val method = findLoaderMethod() ?: return
        with(param) {
            method.hook {
                after {
                    val path = args[0] as? String ?: return@after
                    scope.launch { parse(path)?.let(onLyricsLoaded) }
                }
            }
        }
    }

    private fun findLoaderMethod(): Method? = dexkit.method {
        matcher {
            declaredClass(LYRIC_MANAGER_CLASS)
            addUsingString(LOADER_STRING)
            paramTypes(classOf<String>(), classOf<Boolean>())
        }
    }

    private fun parse(path: String): List<RichLyricLine>? {
        return try {
            val file = File(path)
            if (!file.exists()) return null
            val lyrics = when (file.extension) {
                "krc" -> KrcParser.parse(KrcDecryptor.decrypt(file.readBytes())).richLyricLines
                "lrc" -> LrcParser.parse(file.readText()).lines.toRichLyricLines()
                else -> return null
            }
            lyrics.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            YLog.error(tag = ProviderConfig.LOG_TAG, msg = "Lyric parsing failed: ${e.message}")
            null
        }
    }

    private companion object {
        const val LYRIC_MANAGER_CLASS = "com.kugou.framework.lyric.LyricManager"
        const val LOADER_STRING = "file is not krc or lyc or txt file"
    }
}
