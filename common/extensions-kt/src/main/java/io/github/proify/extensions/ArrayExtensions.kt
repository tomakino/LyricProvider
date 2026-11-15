/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.extensions

/**
 * 将平铺数组 ["k1", "v1", "k2", "v2"] 转换为 Map
 */
fun Array<*>.toPairMap(): Map<String, String> {
    if (this.isEmpty()) return emptyMap()
    val result = mutableMapOf<String, String>()
    for (i in indices step 2) {
        val key = this[i]?.toString() ?: continue
        val value = if (i + 1 < this.size) this[i + 1]?.toString() ?: "" else ""
        result[key] = value
    }
    return result
}