/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.parser

import de.robv.android.xposed.XposedHelpers

/** 安全调用对象方法，任一环节失败返回 null，不抛出异常。 */
fun callMethod(any: Any, name: String, vararg args: Any?): Any? =
    runCatching { XposedHelpers.callMethod(any, name, *args) }.getOrNull()

/**
 * 遍历 Apple Music 原生 Vector（元素为智能指针，需先 `get()` 解引用）。
 * 结构固定为 `size()` -> `get(i)` -> `get()`，[mapper] 负责把原生元素映射为模型对象，
 * 返回 null 的元素会被跳过。
 */
fun <T> parseNativeVector(any: Any, mapper: (native: Any) -> T?): MutableList<T> {
    val size = callMethod(any, "size") as? Long ?: 0L
    val result = ArrayList<T>(size.toInt())
    for (i in 0 until size.toInt()) {
        val pointer = callMethod(any, "get", i) ?: continue
        val native = callMethod(pointer, "get") ?: continue
        mapper(native)?.let { result.add(it) }
    }
    return result
}
