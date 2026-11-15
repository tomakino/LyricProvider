/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lrckit.support

import io.github.proify.lrckit.support.LyricSampleGenerator.DEFAULT_SEED
import io.github.proify.lrckit.support.LyricSampleGenerator.fixedSamples
import io.github.proify.lrckit.support.LyricSampleGenerator.randomSamples
import kotlin.random.Random

/**
 * 样本所属的歌词格式族。
 * 用于给自我迭代测试命名，并在失败时快速定位是哪一类格式回归了。
 */
enum class LyricFormat {
    /** 标准 LRC：`[mm:ss.xx]文本` */
    STANDARD_LRC,

    /** 逐字时间轴：`[mm:ss.xx]<mm:ss.xx>字` */
    WORD_LEVEL,

    /** 一行多个时间戳：`[t1][t2]文本` */
    MULTI_TAG,

    /** 多角色：`v1:` / `bg:` 前缀 */
    MULTI_ROLE,

    /** 元数据与副轨道：`[ar:xxx]`、`[bg:xxx]` */
    METADATA,

    /** 全局偏移：`[offset:xxx]` */
    OFFSET,

    /** 正文包含方括号 */
    BRACKET_TEXT,

    /** 非法行、空行间奏、纯文本噪声 */
    MALFORMED,

    /** 随机组合生成 */
    RANDOM
}

/**
 * 样本的期望事实。
 *
 * 两种解析器分别声明行数：增强解析器会把同一时间点的多段（主唱 + 和声）合并为一条副轨道，
 * 因此其行数可以小于标准 LRC 的行数。
 */
data class SampleExpectation(
    val lrcLines: Int,
    val enhanceLines: Int,
    val enhanceFirstText: String? = null,
    val enhanceFirstSecondary: String? = null,
    val expectsWords: Boolean = false,
    val expectsAlignedRight: Boolean = false,
    val firstBegin: Long? = null
)

/**
 * 一个自校验样本：格式文本 + 期望事实。
 * 生成器同时给出"喂进去的数据"和"应该得到什么"，因此可以作为自我迭代的判据。
 */
data class LyricSample(
    val name: String,
    val format: LyricFormat,
    val raw: String,
    val expectation: SampleExpectation,
    val duration: Long = 0L
)

/**
 * 多格式歌词样本生成器。
 *
 * 负责产出不同歌词格式的数据：
 * - [fixedSamples] 覆盖各类格式与边界，结果完全确定；
 * - [randomSamples] 在固定种子下随机组合格式要素，扩大覆盖但保持可复现。
 *
 * 种子缺省取 [DEFAULT_SEED] 以保证 CI 稳定不抖动，
 * 可用 `gradlew :share:lrckit:test -Plrckit.seed=xxx` 覆盖以扩大覆盖范围。
 */
object LyricSampleGenerator {

    const val DEFAULT_SEED = 20260214L

    private const val SEED_PROPERTY = "lrckit.seed"

    /** 生效种子：优先取系统属性（Gradle 侧由 `-Plrckit.seed` 透传），缺省使用 [DEFAULT_SEED]。 */
    fun seed(): Long = System.getProperty(SEED_PROPERTY)?.toLongOrNull() ?: DEFAULT_SEED

    /** 手工覆盖各类格式与边界的确定性样本。 */
    fun fixedSamples(): List<LyricSample> = listOf(
        LyricSample(
            name = "standard/basic",
            format = LyricFormat.STANDARD_LRC,
            raw = """
                [ti:测试歌曲]
                [00:10.20]第一行歌词
                [00:12.50]第二行歌词
            """.trimIndent(),
            expectation = SampleExpectation(
                lrcLines = 2,
                enhanceLines = 2,
                enhanceFirstText = "第一行歌词",
                firstBegin = 10200L
            )
        ),
        LyricSample(
            name = "word/cjk-no-separator",
            format = LyricFormat.WORD_LEVEL,
            raw = "[00:10.00]<00:10.00>你<00:10.50>好<00:11.00>世<00:11.50>界",
            expectation = SampleExpectation(
                lrcLines = 1,
                enhanceLines = 1,
                enhanceFirstText = "你好世界",
                expectsWords = true,
                firstBegin = 10000L
            )
        ),
        LyricSample(
            name = "word/english-keeps-space",
            format = LyricFormat.WORD_LEVEL,
            raw = "[00:10.00]<00:10.00>Hello <00:10.50>World",
            expectation = SampleExpectation(
                lrcLines = 1,
                enhanceLines = 1,
                enhanceFirstText = "Hello World",
                expectsWords = true
            )
        ),
        LyricSample(
            name = "word/last-word-aligns-line-end",
            format = LyricFormat.WORD_LEVEL,
            raw = """
                [00:10.00]<00:10.00>甲<00:10.50>乙
                [00:12.00]尾行
            """.trimIndent(),
            expectation = SampleExpectation(
                lrcLines = 2,
                enhanceLines = 2,
                enhanceFirstText = "甲乙",
                expectsWords = true,
                firstBegin = 10000L
            )
        ),
        LyricSample(
            name = "word/with-role-prefix",
            format = LyricFormat.WORD_LEVEL,
            raw = "[00:10.00]v1: <00:10.00>你<00:10.40>好",
            expectation = SampleExpectation(
                lrcLines = 1,
                enhanceLines = 1,
                enhanceFirstText = "你好",
                expectsWords = true
            )
        ),
        LyricSample(
            name = "multi-tag/duplicated-line",
            format = LyricFormat.MULTI_TAG,
            raw = "[00:10.00][00:20.00]重复的歌词",
            expectation = SampleExpectation(
                lrcLines = 2,
                enhanceLines = 2,
                enhanceFirstText = "重复的歌词",
                firstBegin = 10000L
            )
        ),
        LyricSample(
            name = "multi-role/v1-and-bg",
            format = LyricFormat.MULTI_ROLE,
            raw = """
                [00:10.00]v1: 主唱内容
                [00:12.00]bg: 背景伴唱
            """.trimIndent(),
            expectation = SampleExpectation(
                lrcLines = 2,
                enhanceLines = 2,
                enhanceFirstText = "主唱内容",
                expectsAlignedRight = true
            )
        ),
        LyricSample(
            name = "metadata/bg-appended-to-previous-line",
            format = LyricFormat.METADATA,
            raw = """
                [00:10.00]Hello World
                [bg: (Background Voice)]
            """.trimIndent(),
            expectation = SampleExpectation(
                lrcLines = 1,
                enhanceLines = 1,
                enhanceFirstText = "Hello World",
                enhanceFirstSecondary = "(Background Voice)"
            )
        ),
        LyricSample(
            name = "metadata/multi-secondary-merge",
            format = LyricFormat.MULTI_ROLE,
            raw = """
                [00:10.00]v1: 主唱
                [00:10.00]bg: 和声一
                [00:10.00]bg: 和声二
            """.trimIndent(),
            expectation = SampleExpectation(
                lrcLines = 3,
                enhanceLines = 1,
                enhanceFirstText = "主唱",
                enhanceFirstSecondary = "和声一\n和声二",
                expectsAlignedRight = true
            )
        ),
        LyricSample(
            name = "bracket-text/content-keeps-brackets",
            format = LyricFormat.BRACKET_TEXT,
            raw = """
                [00:32.99]【/Prologue】
                [00:33.11]何時[人類]向天空築起了[巴別塔]
                [01:15.54][天才]咬下了「苹果」
            """.trimIndent(),
            expectation = SampleExpectation(
                lrcLines = 3,
                enhanceLines = 3,
                enhanceFirstText = "【/Prologue】",
                firstBegin = 32990L
            )
        ),
        LyricSample(
            name = "offset/global-shift",
            format = LyricFormat.OFFSET,
            raw = """
                [offset:1000]
                [00:10.00]歌词
            """.trimIndent(),
            expectation = SampleExpectation(
                lrcLines = 1,
                enhanceLines = 1,
                enhanceFirstText = "歌词",
                firstBegin = 11000L
            )
        ),
        LyricSample(
            name = "offset/shifts-word-timeline-too",
            format = LyricFormat.OFFSET,
            raw = """
                [offset:1000]
                [00:10.00]<00:10.00>甲<00:10.50>乙
                [00:12.00]尾行
            """.trimIndent(),
            expectation = SampleExpectation(
                lrcLines = 2,
                enhanceLines = 2,
                enhanceFirstText = "甲乙",
                expectsWords = true,
                firstBegin = 11000L
            )
        ),
        LyricSample(
            name = "standard/fraction-digits",
            format = LyricFormat.STANDARD_LRC,
            raw = """
                [00:01.5]一位毫秒
                [00:02.50]两位毫秒
                [00:03.500]三位毫秒
            """.trimIndent(),
            expectation = SampleExpectation(
                lrcLines = 3,
                enhanceLines = 3,
                enhanceFirstText = "一位毫秒",
                firstBegin = 1500L
            )
        ),
        LyricSample(
            name = "standard/separator-variants",
            format = LyricFormat.STANDARD_LRC,
            raw = """
                [00:01:50]冒号分隔
                [00 02 50]空格分隔
                [00.03.50]点号分隔
            """.trimIndent(),
            expectation = SampleExpectation(
                lrcLines = 3,
                enhanceLines = 3,
                enhanceFirstText = "冒号分隔",
                firstBegin = 1500L
            )
        ),
        LyricSample(
            name = "malformed/interlude-empty-line",
            format = LyricFormat.MALFORMED,
            raw = """
                [00:00.85]
                [00:05.00]内容
            """.trimIndent(),
            expectation = SampleExpectation(
                lrcLines = 2,
                enhanceLines = 2,
                enhanceFirstText = "",
                firstBegin = 850L
            )
        ),
        LyricSample(
            name = "malformed/plain-noise",
            format = LyricFormat.MALFORMED,
            raw = """
                纯文本没有标签
                [invalid]格式错误
                [00:01.00]正常行
            """.trimIndent(),
            expectation = SampleExpectation(
                lrcLines = 1,
                enhanceLines = 1,
                enhanceFirstText = "正常行",
                firstBegin = 1000L
            )
        )
    )

    /**
     * 固定种子下的多格式随机样本。
     * @param count 样本数量。
     * @param seed 随机种子，默认取 [seed]。
     */
    fun randomSamples(count: Int = 60, seed: Long = seed()): List<LyricSample> {
        val random = Random(seed)
        return List(count) { index -> randomSample(index, random) }
    }

    private val TEXT_POOL = listOf(
        "夜空中最亮的星",
        "Hello World",
        "Fly me to the moon",
        "君の名は",
        "【副歌】",
        "请你不要忘记我",
        "la la la"
    )

    private val SYLLABLE_POOL = listOf("你", "好", "世", "界", "a", "b", "夜", "星")

    private val ROLE_POOL = listOf("", "v1: ", "bg: ")

    private val SEPARATORS = listOf(':', '.', ' ')

    private fun randomSample(index: Int, random: Random): LyricSample {
        val lineCount = random.nextInt(1, 10)
        val withWords = random.nextBoolean()
        val separator = SEPARATORS.random(random)
        // 毫秒位数决定时间粒度：生成的时间必须能被解析器无损还原
        val fractionDigits = random.nextInt(1, 4)
        val granularity = when (fractionDigits) {
            1 -> 100L
            2 -> 10L
            else -> 1L
        }

        // 第一阶段：确定全部行时间点。逐字布局必须使用"本行到下一行"的间距，
        // 若误用"上一行到本行"的间距，末词会越界到下一行之后。
        val begins = mutableListOf<Long>()
        var cursor = alignTo(random.nextLong(0L, 3000L), granularity)
        repeat(lineCount) {
            cursor += alignTo(random.nextLong(2000L, 6000L), granularity).coerceAtLeast(granularity)
            begins += cursor
        }

        val builder = StringBuilder()
        if (random.nextInt(4) == 0) {
            builder.append("[ar:随机歌手]").append('\n')
        }
        if (random.nextInt(3) == 0) {
            builder.append("[offset:").append(random.nextInt(-300, 500)).append(']').append('\n')
        }

        // 第二阶段：逐行生成文本，逐字时间严格落在本行时间窗 [lineBegin, 下一行 begin) 内
        begins.forEachIndexed { position, lineBegin ->
            val span = begins.getOrNull(position + 1)?.minus(lineBegin)
                ?: alignTo(random.nextLong(2000L, 4000L), granularity).coerceAtLeast(granularity)

            builder.append('[').append(formatTime(lineBegin, separator, fractionDigits)).append(']')
            builder.append(ROLE_POOL.random(random))

            if (withWords) {
                val syllableCount = random.nextInt(2, 5)
                val step =
                    alignTo(span / (syllableCount + 1), granularity).coerceAtLeast(granularity)
                var wordTime = lineBegin
                repeat(syllableCount) {
                    builder.append('<')
                        .append(formatTime(wordTime, separator, fractionDigits))
                        .append('>')
                    builder.append(SYLLABLE_POOL.random(random))
                    wordTime += step
                }
            } else {
                builder.append(TEXT_POOL.random(random))
            }
            builder.append('\n')
        }

        return LyricSample(
            name = "random#$index",
            format = LyricFormat.RANDOM,
            raw = builder.toString(),
            expectation = SampleExpectation(
                lrcLines = lineCount,
                enhanceLines = lineCount,
                expectsWords = withWords
            )
        )
    }

    private fun alignTo(value: Long, granularity: Long): Long = value / granularity * granularity

    private fun formatTime(ms: Long, separator: Char, fractionDigits: Int): String {
        val minutes = ms / 60_000
        val seconds = ms % 60_000 / 1000
        val fraction = when (fractionDigits) {
            1 -> ms % 1000 / 100
            2 -> ms % 1000 / 10
            else -> ms % 1000
        }
        return buildString {
            append(minutes.toString().padStart(2, '0'))
            append(separator)
            append(seconds.toString().padStart(2, '0'))
            append(separator)
            append(fraction.toString().padStart(fractionDigits, '0'))
        }
    }
}
