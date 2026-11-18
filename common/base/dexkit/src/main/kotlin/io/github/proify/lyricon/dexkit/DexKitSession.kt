/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.dexkit

import android.util.Log
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.DexKitCacheBridge
import org.luckypray.dexkit.annotations.DexKitExperimentalApi
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindField
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.wrap.DexClass
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.io.Closeable
import java.lang.reflect.Field
import java.lang.reflect.Method

/** 绑定单一宿主的缓存查询会话，用完即关。 */
@OptIn(DexKitExperimentalApi::class)
class DexKitSession internal constructor(
    private val appTag: String,
    private val apkPath: String,
    private val classLoader: ClassLoader,
) : Closeable {

    private val lock = Any()
    private var bridge: DexKitCacheBridge.RecyclableBridge? = null

    /** 缓存查询：类，键由查询条件自动生成。 */
    fun classData(query: FindClass.() -> Unit): DexClass? = runCatching {
        obtainBridge().getClass { findClass -> findClass.apply(query) }
    }.onFailure {
        Log.w(TAG, "class query failed", it)
    }.getOrNull()

    /** 缓存查询：方法，键由查询条件自动生成。 */
    fun methodData(query: FindMethod.() -> Unit): DexMethod? = runCatching {
        obtainBridge().getMethod { findMethod -> findMethod.apply(query) }
    }.onFailure {
        Log.w(TAG, "method query failed", it)
    }.getOrNull()

    /** 缓存查询：字段，键由查询条件自动生成。 */
    fun fieldData(query: FindField.() -> Unit): DexField? = runCatching {
        obtainBridge().getField { findField -> findField.apply(query) }
    }.onFailure {
        Log.w(TAG, "field query failed", it)
    }.getOrNull()

    fun classOf(query: FindClass.() -> Unit): Class<*>? = classData(query)?.toReflect()

    fun method(query: FindMethod.() -> Unit): Method? = methodData(query)?.toReflect()

    fun field(query: FindField.() -> Unit): Field? = fieldData(query)?.toReflect()

    /** 缓存查询：所有匹配的类。 */
    fun classDatas(query: FindClass.() -> Unit): List<DexClass> = runCatching {
        obtainBridge().getClasses { findClass -> findClass.apply(query) }
    }.onFailure {
        Log.w(TAG, "classes query failed", it)
    }.getOrElse { emptyList() }

    /** 缓存查询：所有匹配的方法。 */
    fun methodDatas(query: FindMethod.() -> Unit): List<DexMethod> = runCatching {
        obtainBridge().getMethods { findMethod -> findMethod.apply(query) }
    }.onFailure {
        Log.w(TAG, "methods query failed", it)
    }.getOrElse { emptyList() }

    /** 缓存查询：所有匹配的字段。 */
    fun fieldDatas(query: FindField.() -> Unit): List<DexField> = runCatching {
        obtainBridge().getFields { findField -> findField.apply(query) }
    }.onFailure {
        Log.w(TAG, "fields query failed", it)
    }.getOrElse { emptyList() }

    fun classes(query: FindClass.() -> Unit): List<Class<*>> = classDatas(query).mapNotNull { it.toReflect() }

    fun methods(query: FindMethod.() -> Unit): List<Method> = methodDatas(query).mapNotNull { it.toReflect() }

    fun fields(query: FindField.() -> Unit): List<Field> = fieldDatas(query).mapNotNull { it.toReflect() }

    /** 借用原始 bridge 做无缓存的后续查询或显式键读取。 */
    fun <T> withBridge(block: (DexKitBridge) -> T): T? = runCatching {
        var value: T? = null
        obtainBridge().withBridge { dexKit -> value = block(dexKit) }
        value
    }.onFailure {
        Log.w(TAG, "bridge operation failed", it)
    }.getOrNull()

    /** 查询语义变化或缓存损坏时清理本命名空间。 */
    fun clearCache() {
        DexKitCacheBridge.clearCache(appTag)
    }

    /** 释放底层 bridge，之后的查询会重新借用。 */
    override fun close() {
        synchronized(lock) {
            runCatching { bridge?.close() }
            bridge = null
        }
    }

    private fun obtainBridge(): DexKitCacheBridge.RecyclableBridge = synchronized(lock) {
        bridge ?: DexKitCacheBridge.create(appTag, apkPath).also { bridge = it }
    }

    private fun DexMethod.toReflect(): Method? =
        runCatching { getMethodInstance(classLoader) }.getOrNull()

    private fun DexClass.toReflect(): Class<*>? =
        runCatching { getInstance(classLoader) }.getOrNull()

    private fun DexField.toReflect(): Field? =
        runCatching { getFieldInstance(classLoader) }.getOrNull()

    private companion object {
        const val TAG = "DexKitSession"
    }
}
