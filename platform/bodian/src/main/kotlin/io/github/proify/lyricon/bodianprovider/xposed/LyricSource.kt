/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.bodianprovider.xposed

import android.content.Context
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.lyric.model.RichLyricLine
import java.io.File

/**
 * 歌词文件读取。
 *
 * 波点的歌词由 Dart 侧下载后落盘，再把绝对路径通过 MethodChannel 交给原生
 * （见 [FlutterLyricBridge]）。文件是**明文**的，App 侧只是
 * `new String(bytes)` 后按内容判类型，所以这里不需要任何解密。
 *
 * 落盘位置为 `cacheDir/lyric/{rid}.lrcx`（逐字）或 `{rid}.lrc`（普通），
 * 拿不到 Dart 给的路径时按这个规则兜底扫。
 */
object LyricSource {

    private const val TAG = "BodianLyricSource"
    private const val LYRIC_DIR = "lyric"

    private var cacheRoot: File? = null

    fun initialize(context: Context) {
        cacheRoot = context.cacheDir
        YLog.debug(tag = TAG, msg = "cacheDir=${context.cacheDir.absolutePath}")
    }

    /** Dart 给的绝对路径优先；没有就按 rid 在 `cacheDir/lyric` 里找 */
    fun locate(rid: String?, lyricPath: String?): File? {
        lyricPath?.takeIf { it.isNotBlank() }
            ?.let { File(it) }
            ?.takeIf { it.isFile && it.length() > 0 }
            ?.let { return it }

        if (rid.isNullOrBlank()) return null
        val dir = cacheRoot?.resolve(LYRIC_DIR)?.takeIf { it.isDirectory } ?: return null

        listOf("$rid.lrcx", "$rid.lrc", "$rid-mv.lrcx", "$rid-mv.lrc").forEach { name ->
            val file = File(dir, name)
            if (file.isFile && file.length() > 0) return file
        }
        return null
    }

    fun read(file: File): String? = runCatching {
        file.readText(Charsets.UTF_8).takeIf { it.isNotBlank() }
    }.onFailure {
        YLog.error(tag = TAG, msg = "read failed: ${file.absolutePath}", e = it)
    }.getOrNull()

    fun parse(file: File, durationMs: Long): List<RichLyricLine> {
        val content = read(file) ?: return emptyList()
        val lines = KuwoLyricParser.parse(content, durationMs)
        YLog.debug(
            tag = TAG,
            msg = "parsed ${lines.size} lines from ${file.name} " +
                    "(wordwise=${lines.any { !it.words.isNullOrEmpty() }})"
        )
        return lines
    }

    /**
     * 诊断用：把 `cacheDir/lyric` 下的文件名打进 logcat。
     * 命名规则变了的话，看这里就能发现。
     */
    fun dumpLyricDir(limit: Int = 20) {
        val dir = cacheRoot?.resolve(LYRIC_DIR)
        if (dir == null || !dir.isDirectory) {
            YLog.warn(tag = TAG, msg = "lyric dir missing: ${dir?.absolutePath}")
            return
        }
        val files = dir.listFiles().orEmpty()
        YLog.debug(tag = TAG, msg = "lyric dir ${dir.absolutePath}, ${files.size} files")
        files.take(limit).forEach { YLog.debug(tag = TAG, msg = "  ${it.name} (${it.length()}B)") }
    }
}
