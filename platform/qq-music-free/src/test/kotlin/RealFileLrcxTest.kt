/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
import io.github.proify.lyricon.lyric.model.RichLyricLine
import org.junit.Assert.assertEquals
import org.junit.Test

class RealFileLrcxTest {

    private fun load(name: String): List<RichLyricLine>? {
        val stream = javaClass.getResourceAsStream("/" + name) ?: return null
        return stream.use { KuwoLyricParser.parse(it.readBytes().toString(Charsets.UTF_8)) }
    }

    @Test
    fun `样本 75407134`() {
        val lines = load("75407134.lrcx") ?: return
        println("75407134 lines=" + lines.size)

        val sleep = lines.firstOrNull { it.text?.contains("Sleep") == true }
        assertEquals("睡眠不曾降临 我只能在阳光下窒息", sleep?.translation)
    }

    @Test
    fun `样本 668844894`() {
        val lines = load("668844894.lrcx") ?: return
        println("668844894 lines=" + lines.size)

        val home = lines.firstOrNull { it.text?.contains("I can't go home") == true }
        val love = lines.firstOrNull { it.text?.contains("They don't love me") == true }

        assertEquals("我回不了家", home?.translation)
        assertEquals("他们不再像从前那样爱我，不再爱了", love?.translation)
    }

    @Test
    fun `样本 51571386`() {
        val lines = load("51571386.lrcx") ?: return
        println("51571386 lines=" + lines.size)

        val touch = lines.firstOrNull { it.text?.contains("I wanna feel your touch") == true }
        val ember = lines.firstOrNull { it.text?.contains("burning") == true }

        assertEquals("我想要去感受你的触摸", touch?.translation)
        assertEquals("我就像余烬在暗火焚烧", ember?.translation)
    }
}
