/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lrckit

import io.github.proify.lrckit.support.LyricInvariants
import io.github.proify.lrckit.support.LyricSample
import io.github.proify.lrckit.support.LyricSampleGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * 多格式歌词数据的自我迭代测试。
 *
 * 闭环：生成不同歌词格式的数据 → 送入 [LrcParser] 与 [EnhanceLrcParser] → 校验不变量与期望事实。
 * 任意一条失败都意味着解析器偏离了同族解析器（QRC / YRC / KRC）的既定约定。
 *
 * 样本种子可用 `gradlew :share:lrckit:test -Plrckit.seed=xxx` 覆盖以扩大覆盖范围，默认种子固定以保证 CI 可复现。
 */
class LyricFormatIterationTest {

    @TestFactory
    @DisplayName("固定样本：各类歌词格式解析后满足全部不变量")
    fun fixedFormatSamples(): List<DynamicTest> =
        LyricSampleGenerator.fixedSamples().map { sample ->
            DynamicTest.dynamicTest(sample.name) { verify(sample) }
        }

    @TestFactory
    @DisplayName("随机样本：固定种子生成的多格式数据满足全部不变量")
    fun randomizedFormatSamples(): List<DynamicTest> =
        LyricSampleGenerator.randomSamples().mapIndexed { _, sample ->
            DynamicTest.dynamicTest("${sample.name}-line${sample.expectation.lrcLines}") {
                verify(sample)
            }
        }

    @Test
    @DisplayName("offset 元数据与解析后应用偏移等价")
    fun offsetMetadataEqualsPostOffset() {
        val raw = "[00:10.00]第一行\n[00:12.00]第二行"
        val offset = 1500L

        val baseline = LrcParser.parse(raw)
        val withMeta = LrcParser.parse("[offset:$offset]\n$raw")
        val shifted = baseline.applyOffset(offset)

        assertEquals(
            shifted.lines.map { it.begin },
            withMeta.lines.map { it.begin },
            "offset 元数据未与解析后偏移的 begin 保持一致"
        )
        assertEquals(
            shifted.lines.map { it.end },
            withMeta.lines.map { it.end },
            "offset 元数据未与解析后偏移的 end 保持一致"
        )
    }

    @Test
    @DisplayName("样本种子：相同种子可复现，不同种子产生不同数据")
    fun seedDrivesSamplesReproducibly() {
        val first = LyricSampleGenerator.randomSamples(count = 5, seed = 1L).map { it.raw }
        val second = LyricSampleGenerator.randomSamples(count = 5, seed = 1L).map { it.raw }
        val other = LyricSampleGenerator.randomSamples(count = 5, seed = 2L).map { it.raw }

        assertEquals(first, second, "相同种子必须产生完全一致的样本，否则自迭代不可复现")
        assertNotEquals(first, other, "不同种子应当产生不同的样本数据")
    }

    @Test
    @DisplayName("样本种子：系统属性可覆盖，默认值固定")
    fun seedIsStableOrOverridable() {
        val overridden = System.getProperty("lrckit.seed")?.toLongOrNull()
        val expected = overridden ?: LyricSampleGenerator.DEFAULT_SEED

        assertEquals(expected, LyricSampleGenerator.seed(), "种子读取链路与系统属性不一致")
    }

    private fun verify(sample: LyricSample) {
        withSampleContext(sample) {
            LyricInvariants.assertLrc(LrcParser.parse(sample.raw, sample.duration), sample)
        }
        withSampleContext(sample) {
            LyricInvariants.assertEnhance(
                EnhanceLrcParser.parse(sample.raw, sample.duration),
                sample
            )
        }
    }

    /** 断言失败时把生成用的原始歌词附在消息后，便于直接从测试报告定位问题样本。 */
    private fun withSampleContext(sample: LyricSample, block: () -> Unit) {
        try {
            block()
        } catch (error: AssertionError) {
            throw AssertionError(
                "${error.message}\n----- ${sample.name} raw -----\n${sample.raw}",
                error
            )
        }
    }
}
