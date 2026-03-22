/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.extensions

import io.github.proify.lyricon.lyric.model.interfaces.ILyricLine
import kotlin.math.abs

/**
 * 在已排序的列表中查找与 targetBegin 最接近且误差在 tolerance 内的 LyricLine
 */
fun <T : ILyricLine> List<T>.findClosest(targetBegin: Long, tolerance: Long): T? {
    if (this.isEmpty()) return null

    // 使用二分查找找到插入点
    val index = this.binarySearch { it.begin.compareTo(targetBegin) }

    // 如果精确匹配到了 (index >= 0)
    if (index >= 0) return this[index]

    // 如果没匹配到，计算插入点附近的元素
    val insertionPoint = -(index + 1)

    // 检查插入点位置及其前一个位置，看哪个更接近且在误差内
    val candidates = mutableListOf<T>()
    if (insertionPoint < size) candidates.add(this[insertionPoint])
    if (insertionPoint > 0) candidates.add(this[insertionPoint - 1])

    return candidates
        .filter { abs(it.begin - targetBegin) <= tolerance }
        .minByOrNull { abs(it.begin - targetBegin) }
}