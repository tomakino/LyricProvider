/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.extensions.android

import android.annotation.SuppressLint
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.highcapable.yukihookapi.extension.kavaref.toClass
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam
import java.lang.reflect.Field
import java.util.concurrent.CopyOnWriteArraySet

object Flyme {
    private const val TAG = "Flyme"

    const val FLAG_ALWAYS_SHOW_TICKER_HOOK = 0x01000000
    const val FLAG_ONLY_UPDATE_TICKER_HOOK = 0x02000000

    private val unhooks = CopyOnWriteArraySet<() -> Unit>()

    private var cachedAlwaysShowField: Field? = null
    private var cachedOnlyUpdateField: Field? = null

    private val spoofMap = mapOf(
        "ro.product.model" to "meizu 16th Plus",
        "ro.product.brand" to "meizu",
        "ro.product.manufacturer" to "Meizu",
        "ro.product.device" to "m1892",
        "ro.build.display.id" to "Flyme",
        "ro.build.product" to "meizu_16thPlus_CN",
        "ro.meizu.product.model" to "m1892"
    )

    fun unlock() {
        unhooks.forEach { it() }
        unhooks.clear()
    }

    @SuppressLint("PrivateApi")
    context(param: PackageParam)
    fun mock(loader: ClassLoader) {
        try {
            initFieldsCache()

            val buildClass = "android.os.Build".toClass(loader)
            val buildFields = mapOf(
                "BRAND" to "meizu",
                "MANUFACTURER" to "Meizu",
                "DEVICE" to "m1892",
                "DISPLAY" to "Flyme",
                "PRODUCT" to "meizu_16thPlus_CN",
                "MODEL" to "meizu 16th Plus"
            )
            buildFields.forEach { (name, value) ->
                buildClass.getDeclaredField(name).apply { isAccessible = true }.set(null, value)
            }

            val systemProperties = "android.os.SystemProperties".toClass(loader)
            val keyHook = systemProperties.resolve().firstMethod {
                name = "get"
                parameters(String::class.java)
            }.hook {
                before {
                    val key = args[0] as? String ?: return@before
                    spoofMap[key]?.let { result = it }
                }
            }
            val keyWithDefaultHook = systemProperties.resolve().firstMethod {
                name = "get"
                parameters(String::class.java, String::class.java)
            }.hook {
                before {
                    val key = args[0] as? String ?: return@before
                    spoofMap[key]?.let { result = it }
                }
            }
            unhooks += { keyHook.remove() }
            unhooks += { keyWithDefaultHook.remove() }

            val getFieldHook = Class::class.java.resolve().firstMethod {
                name = "getField"
                parameters(String::class.java)
            }.hook {
                after {
                    val name = args[0] as? String ?: return@after
                    applyCachedFlag(name) { result = it }
                }
            }
            val getDeclaredFieldHook = Class::class.java.resolve().firstMethod {
                name = "getDeclaredField"
                parameters(String::class.java)
            }.hook {
                after {
                    val name = args[0] as? String ?: return@after
                    applyCachedFlag(name) { result = it }
                }
            }
            unhooks += { getFieldHook.remove() }
            unhooks += { getDeclaredFieldHook.remove() }
        } catch (t: Throwable) {
            YLog.error(tag = TAG, msg = "Flyme Mock Error: ${t.message}")
        }
    }

    private fun initFieldsCache() {
        try {
            cachedAlwaysShowField =
                Flyme::class.java.getDeclaredField("FLAG_ALWAYS_SHOW_TICKER_HOOK")
            cachedOnlyUpdateField =
                Flyme::class.java.getDeclaredField("FLAG_ONLY_UPDATE_TICKER_HOOK")
        } catch (e: Exception) {
            YLog.error(tag = TAG, msg = "Failed to cache fields: $e")
        }
    }

    private fun applyCachedFlag(name: String, setResult: (Any?) -> Unit) {
        when (name) {
            "FLAG_ALWAYS_SHOW_TICKER" -> cachedAlwaysShowField?.let(setResult)
            "FLAG_ONLY_UPDATE_TICKER" -> cachedOnlyUpdateField?.let(setResult)
        }
    }
}
