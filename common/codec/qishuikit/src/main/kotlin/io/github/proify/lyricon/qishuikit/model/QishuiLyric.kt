/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.qishuikit.model

import io.github.proify.extensions.findClosest
import io.github.proify.lrckit.LrcParser
import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.qishuikit.parser.KrcLyricParser
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class QishuiLyric(
    val id: String,
    val name: String? = null,
    val artist: String? = null,
    val duration: Long = 0,
    val type: String? = null,
    val content: String? = null,
    @SerialName("lang_translations") val langTranslations: Map<String, QishuiTranslation>? = null
) {

    fun toSong(): Song? {
        val lines = parseMain() ?: return null
        if (lines.isEmpty()) return null

        val translations = parseTranslation()

        val rich = lines.map { line ->
            RichLyricLine(
                begin = line.begin,
                end = line.end,
                duration = line.duration,
                text = line.text,
                words = line.words,
                translation = translations.findClosest(line.begin, 50)
                    ?.text
                    ?.takeUnless { it.trim() == "//" }
            )
        }

        return Song(
            id = id,
            name = name.orEmpty(),
            artist = artist.orEmpty(),
            duration = duration,
            lyrics = rich
        )
    }

    private fun parseMain(): List<LyricLine>? = when (type?.lowercase()) {
        "lrc" -> LrcParser.parse(content, duration).lines
        else -> KrcLyricParser.parse(content)
    }

    private fun parseTranslation(): List<LyricLine> {
        val translation = pickTranslation() ?: return emptyList()
        val text = translation.content?.takeIf { it.isNotBlank() } ?: return emptyList()

        return when (translation.type?.lowercase()) {
            "krc" -> KrcLyricParser.parse(text)
            "lrc" -> LrcParser.parse(text, duration).lines
            else -> emptyList()
        }
    }

    private fun pickTranslation(): QishuiTranslation? {
        val translations = langTranslations ?: return null
        val key = pickLanguageKey(translations.keys) ?: return null
        return translations[key]
    }

    private fun pickLanguageKey(keys: Set<String>): String? {
        if (keys.isEmpty()) return null
        if (keys.size == 1) return keys.first()

        val locale = Locale.getDefault()
        val language = locale.language

        keys.firstOrNull { it.equals(language, ignoreCase = true) }?.let { return it }

        val systemTag = buildString {
            append(language.uppercase())
            if (locale.script.isNotEmpty()) append("-").append(locale.script.uppercase())
            if (locale.country.isNotEmpty()) append("-").append(locale.country.uppercase())
        }
        keys.firstOrNull { it.equals(systemTag, ignoreCase = true) }?.let { return it }

        if (language == "zh") {
            val country = locale.country.uppercase()
            keys.firstOrNull { it.equals("ZH-HANS-$country", ignoreCase = true) }?.let { return it }
            keys.firstOrNull { it.equals("ZH-HANT-$country", ignoreCase = true) }?.let { return it }
        }

        return keys.firstOrNull { it.startsWith(language, ignoreCase = true) }
    }
}
