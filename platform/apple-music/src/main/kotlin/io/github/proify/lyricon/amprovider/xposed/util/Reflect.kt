/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.util

import com.highcapable.kavaref.extension.isStatic
import com.highcapable.kavaref.extension.makeAccessible

// 按「名称 + 参数个数」匹配，调用失败一律返回 null。

/** 调用实例方法。 */
fun Any.callMethodOrNull(name: String, vararg args: Any?): Any? = runCatching {
    javaClass.methods
        .first { it.name == name && it.parameterCount == args.size }
        .apply { makeAccessible() }
        .invoke(this, *args)
}.getOrNull()

/** 调用静态方法。 */
fun Class<*>.callStaticMethodOrNull(name: String, vararg args: Any?): Any? = runCatching {
    methods
        .first {
            it.name == name &&
                    it.parameterCount == args.size &&
                    it.isStatic
        }
        .apply { makeAccessible() }
        .invoke(null, *args)
}.getOrNull()

/** 用无参构造器创建实例。 */
fun Class<*>.newInstanceOrNull(): Any? = runCatching {
    getDeclaredConstructor().apply { makeAccessible() }.newInstance()
}.getOrNull()
