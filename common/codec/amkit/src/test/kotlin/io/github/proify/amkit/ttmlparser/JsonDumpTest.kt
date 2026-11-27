/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.amkit.ttmlparser

import io.github.proify.amkit.ttmlparser.model.LyricsDocument
import io.github.proify.amkit.ttmlparser.model.LyricsLine
import io.github.proify.amkit.ttmlparser.model.LyricsText
import io.github.proify.amkit.ttmlparser.model.LyricsWord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** 把真实样本的解析结果渲染成 JSON，便于核对结构。 */
class JsonDumpTest {

    private val parser = TtmlLyricsParser()

    @Test
    fun `打印真实样本的 JSON 结构`() {
        val sample = File("src/test/resources/samples")
            .listFiles { file -> file.extension == "ttml" }
            .orEmpty()
            .sortedBy { it.name }
            .first()

        val element = parser.parse(sample.readText()).toJson()
        val text = Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), element)
        val lines = text.lines()

        println("=== ${sample.name} ===")
        println(lines.take(DUMP_LINES).joinToString("\n"))
        if (lines.size > DUMP_LINES) {
            println("... ${lines.size - DUMP_LINES} more lines")
        }

        assertTrue(text.contains("\"timing\""))
        assertTrue(text.contains("\"sections\""))
    }

    private fun LyricsDocument.toJson(): JsonObject = JsonObject(
        mapOf(
            "language" to language.toJson(),
            "timing" to JsonPrimitive(timing.name),
            "durationMs" to duration?.inWholeMilliseconds.toJson(),
            "leadingSilenceMs" to leadingSilence?.inWholeMilliseconds.toJson(),
            "audio" to (audio?.let { value ->
                JsonObject(
                    mapOf(
                        "role" to value.role.toJson(),
                        "lyricOffsetMs" to value.lyricOffset?.inWholeMilliseconds.toJson(),
                    )
                )
            } ?: JsonNull),
            "metadata" to JsonObject(
                mapOf(
                    "title" to metadata.title.toJson(),
                    "language" to metadata.language.toJson(),
                    "songwriters" to JsonArray(metadata.songwriters.map(::JsonPrimitive)),
                    "meta" to JsonObject(
                        metadata.meta.mapValues { (_, values) ->
                            JsonPrimitive(values.joinToString(separator = ","))
                        }
                    ),
                )
            ),
            "agents" to JsonArray(
                agents.map { agent ->
                    JsonObject(
                        mapOf(
                            "id" to JsonPrimitive(agent.id),
                            "type" to agent.type.toJson(),
                            "names" to JsonArray(
                                agent.names.map { name ->
                                    JsonObject(
                                        mapOf(
                                            "text" to JsonPrimitive(name.text),
                                            "type" to name.type.toJson(),
                                        )
                                    )
                                }
                            ),
                        )
                    )
                }
            ),
            "sections" to JsonArray(
                sections.map { section ->
                    JsonObject(
                        mapOf(
                            "songPart" to section.songPart.toJson(),
                            "startMs" to section.start?.inWholeMilliseconds.toJson(),
                            "endMs" to section.end?.inWholeMilliseconds.toJson(),
                            "lines" to JsonArray(section.lines.map { it.toJson() }),
                        )
                    )
                }
            ),
        )
    )

    private fun LyricsLine.toJson(): JsonObject = JsonObject(
        mapOf(
            "key" to key.toJson(),
            "agentId" to agentId.toJson(),
            "startMs" to start?.inWholeMilliseconds.toJson(),
            "endMs" to end?.inWholeMilliseconds.toJson(),
            "text" to JsonPrimitive(text),
            "words" to JsonArray(words.map { it.toJson() }),
            "background" to JsonArray(background.map { it.toJson() }),
            "translations" to JsonArray(translations.map { it.toJson() }),
            "transliterations" to JsonArray(transliterations.map { it.toJson() }),
        )
    )

    private fun LyricsWord.toJson(): JsonObject = JsonObject(
        mapOf(
            "text" to JsonPrimitive(text),
            "startMs" to start?.inWholeMilliseconds.toJson(),
            "endMs" to end?.inWholeMilliseconds.toJson(),
        )
    )

    private fun LyricsText.toJson(): JsonObject = JsonObject(
        mapOf(
            "text" to JsonPrimitive(text),
            "language" to language.toJson(),
            "type" to type.toJson(),
            "autoCreated" to JsonPrimitive(autoCreated),
            "words" to JsonArray(words.map { it.toJson() }),
            "background" to JsonArray(background.map { it.toJson() }),
        )
    )

    private fun String?.toJson(): JsonElement = this?.let(::JsonPrimitive) ?: JsonNull

    private fun Long?.toJson(): JsonElement = this?.let(::JsonPrimitive) ?: JsonNull

    private companion object {
        const val DUMP_LINES = 160
    }
}
