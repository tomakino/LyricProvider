/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.cache

import android.content.Context
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.extensions.deflate
import io.github.proify.extensions.inflate
import io.github.proify.extensions.json
import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import kotlinx.serialization.Serializable
import java.io.File
import java.util.Locale

/** 歌词磁盘缓存：deflate JSON，按语言分目录。 */
object LyricsCache {

    private const val CACHE_VERSION = 21

    private var baseDir: File? = null

    fun initialize(context: Context) {
        if (baseDir != null) return
        val languageTag = Locale.getDefault().toLanguageTag()
        // 映射逻辑变更时递增版本，让旧结果自动失效
        baseDir = File(context.filesDir, "lyricon/lyrics/v$CACHE_VERSION/$languageTag")
            .apply { mkdirs() }
    }

    fun load(id: String): Song? = runCatching {
        val file = fileOf(id).takeIf { it.exists() } ?: return null
        val cached = json.decodeFromString<CachedSong>(
            file.readBytes().inflate().toString(Charsets.UTF_8)
        )
        cached.toSong()
    }.onFailure {
        YLog.warn(tag = "AMProvider", msg = "LyricsCache: 读取失败 $id")
    }.getOrNull()

    fun save(song: Song): Boolean = runCatching {
        val id = song.id ?: return false
        val file = fileOf(id)
        file.parentFile?.mkdirs()
        val encoded = json.encodeToString(CachedSong.fromSong(song))
        file.writeBytes(encoded.toByteArray(Charsets.UTF_8).deflate())
    }.onFailure {
        YLog.error(tag = "AMProvider", msg = "LyricsCache: 写入失败")
    }.isSuccess

    fun has(id: String): Boolean = fileOf(id).exists()

    private fun fileOf(id: String): File =
        File(baseDir, "$id.json.gz")

    @Serializable
    private data class CachedWord(
        val text: String,
        val begin: Long = 0,
        val duration: Long = 0,
        val end: Long = 0,
    )

    @Serializable
    private data class CachedLine(
        val begin: Long = 0,
        val end: Long = 0,
        val duration: Long = 0,
        val text: String? = null,
        val words: List<CachedWord> = emptyList(),
        val secondary: String? = null,
        val secondaryWords: List<CachedWord> = emptyList(),
        val translation: String? = null,
        val translationWords: List<CachedWord> = emptyList(),
        val roma: String? = null,
        val alignedRight: Boolean = false,
    )

    @Serializable
    private data class CachedSong(
        val id: String,
        val name: String? = null,
        val artist: String? = null,
        val duration: Long = 0,
        val lines: List<CachedLine> = emptyList(),
    ) {
        fun toSong(): Song = Song(
            id = id,
            name = name,
            artist = artist,
            duration = duration,
            lyrics = lines.map { it.toLyricLine() }.toMutableList()
        )

        companion object {
            fun fromSong(song: Song): CachedSong = CachedSong(
                id = song.id.orEmpty(),
                name = song.name,
                artist = song.artist,
                duration = song.duration,
                lines = song.lyrics.orEmpty().map { CachedLine.fromLine(it) }
            )
        }
    }

    private fun CachedLine.toLyricLine(): RichLyricLine = RichLyricLine().apply {
        begin = this@toLyricLine.begin
        end = this@toLyricLine.end
        duration = this@toLyricLine.duration
        text = this@toLyricLine.text
        words = this@toLyricLine.words.map { it.toLyricWord() }.toMutableList()
        secondary = this@toLyricLine.secondary
        secondaryWords = this@toLyricLine.secondaryWords.map { it.toLyricWord() }.toMutableList()
        translation = this@toLyricLine.translation
        translationWords = this@toLyricLine.translationWords.map { it.toLyricWord() }.toMutableList()
        roma = this@toLyricLine.roma
        isAlignedRight = this@toLyricLine.alignedRight
    }

    private fun CachedWord.toLyricWord(): LyricWord = LyricWord(
        text = text,
        begin = begin,
        duration = duration,
        end = end
    )

    private fun CachedLine.Companion.fromLine(line: RichLyricLine): CachedLine = CachedLine(
        begin = line.begin,
        end = line.end,
        duration = line.duration,
        text = line.text,
        words = line.words.orEmpty().map {
            CachedWord(it.text.orEmpty(), it.begin, it.duration, it.end)
        },
        secondary = line.secondary,
        secondaryWords = line.secondaryWords.orEmpty().map {
            CachedWord(it.text.orEmpty(), it.begin, it.duration, it.end)
        },
        translation = line.translation,
        translationWords = line.translationWords.orEmpty().map {
            CachedWord(it.text.orEmpty(), it.begin, it.duration, it.end)
        },
        roma = line.roma,
        alignedRight = line.isAlignedRight
    )

}
