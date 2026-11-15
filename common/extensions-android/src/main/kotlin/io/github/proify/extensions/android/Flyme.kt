/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.extensions.android

import android.annotation.SuppressLint
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Field
import java.util.concurrent.CopyOnWriteArraySet

object Flyme {
    const val FLAG_ALWAYS_SHOW_TICKER_HOOK = 0x01000000
    const val FLAG_ONLY_UPDATE_TICKER_HOOK = 0x02000000

    private val unhooks = CopyOnWriteArraySet<XC_MethodHook.Unhook>()

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
        unhooks.forEach { it.unhook() }
        unhooks.clear()
    }

    @SuppressLint("PrivateApi")
    fun mock(loader: ClassLoader) {
        try {
            initFieldsCache()

            val buildClass = XposedHelpers.findClass("android.os.Build", loader)
            val buildFields = mapOf(
                "BRAND" to "meizu",
                "MANUFACTURER" to "Meizu",
                "DEVICE" to "m1892",
                "DISPLAY" to "Flyme",
                "PRODUCT" to "meizu_16thPlus_CN",
                "MODEL" to "meizu 16th Plus"
            )
            buildFields.forEach { (k, v) ->
                XposedHelpers.setStaticObjectField(buildClass, k, v)
            }

            val spClass = XposedHelpers.findClass("android.os.SystemProperties", loader)
            val spHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val key = param.args[0] as? String ?: return
                    spoofMap[key]?.let { param.result = it }
                }
            }
            unhooks += XposedHelpers.findAndHookMethod(spClass, "get", String::class.java, spHook)
            unhooks += XposedHelpers.findAndHookMethod(
                spClass,
                "get",
                String::class.java,
                String::class.java,
                spHook
            )

            val fieldHook = GetFieldMethodHook()
            unhooks += XposedHelpers.findAndHookMethod(
                Class::class.java,
                "getField",
                String::class.java,
                fieldHook
            )
            unhooks += XposedHelpers.findAndHookMethod(
                Class::class.java,
                "getDeclaredField",
                String::class.java,
                fieldHook
            )

        } catch (t: Throwable) {
            XposedBridge.log("Flyme Mock Error: ${t.message}")
        }
    }

    private fun initFieldsCache() {
        try {
            cachedAlwaysShowField =
                Flyme::class.java.getDeclaredField("FLAG_ALWAYS_SHOW_TICKER_HOOK")
            cachedOnlyUpdateField =
                Flyme::class.java.getDeclaredField("FLAG_ONLY_UPDATE_TICKER_HOOK")
        } catch (e: Exception) {
            XposedBridge.log("Failed to cache fields: $e")
        }
    }

    private class GetFieldMethodHook : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            val name = param.args[0] as? String ?: return

            when (name) {
                "FLAG_ALWAYS_SHOW_TICKER" -> {
                    cachedAlwaysShowField?.let { param.result = it }
                }

                "FLAG_ONLY_UPDATE_TICKER" -> {
                    cachedOnlyUpdateField?.let { param.result = it }
                }
            }
        }
    }
}