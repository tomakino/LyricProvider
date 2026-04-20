/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.kgprovider.xposed.kugou

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

open class KuGou : KuGouBase() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    protected val TAG = "KuGouProvider"

    override fun shouldHookProcess(): Boolean {
        return processName.endsWith(":support")
                || processName.endsWith(".support")
    }

    override fun onAppCreate() {
        //hookSettings()
    }
//
//    fun hookSettings() {
//        val clazz = dexKitBridge.findClass {
//            matcher {
//                className = "com.kugou.framework.setting.operator.SettingPrefs"
//            }
//        }.singleOrNull()
//
//        val keyMethods = clazz?.findMethod {
//            matcher {
//                addUsingString("player_lyric_translate_type")
//            }
//        }
//
//        val setMethod = keyMethods?.findMethod {
//            matcher {
//                paramTypes(Boolean::class.java)
//            }
//        }?.singleOrNull()?.getMethodInstance(appClassLoader!!)
//
//        val getMethod = keyMethods?.findMethod {
//            matcher {
//                paramCount = 0
//            }
//        }?.singleOrNull()?.getMethodInstance(appClassLoader!!)
//
//        YLog.debug(tag = TAG, msg = "setMethod: $setMethod")
//        YLog.debug(tag = TAG, msg = "getMethod: $getMethod")
//
//        if (setMethod != null) {
//           val result = XposedBridge.hookMethod(setMethod, object : XC_MethodHook() {
//                override fun afterHookedMethod(param: MethodHookParam) {
//                    YLog.debug(tag = TAG, msg = "afterHookedMethod: setDisplayTranslation")
//                    val args = param.args[0] as Boolean
//                    YLog.debug(tag = TAG, msg = "setDisplayTranslation: $args")
//                    provider?.player?.setDisplayTranslation(args)
//                }
//            })
//            YLog.debug(tag = TAG, msg = "hook setMethod result: $result")
//        } else {
//            YLog.error(tag = TAG, msg = "Failed to get setMethod of SettingPrefs")
//        }
//
//        if (getMethod == null) {
//            YLog.error(tag = TAG, msg = "Failed to get getMethod of SettingPrefs")
//        } else {
//            fun findGetInstance(): Method? {
//                val settingManagerClass =
//                    "com.kugou.framework.setting.operator.SettingPrefs".toClass()
//                val declaredMethods = settingManagerClass.declaredMethods
//
//                return declaredMethods.find { method ->
//                    // YLog.debug(tag = TAG, msg = "findmethod: ${method.name}")
//
//                    val modifiers = method.modifiers
//                    val isStatic = java.lang.reflect.Modifier.isStatic(modifiers)
//                    val isPublic = java.lang.reflect.Modifier.isPublic(modifiers)
//
//                    method.parameterCount == 0
//                            && isStatic
//                            && isPublic &&
//                            method.returnType.name == "com.kugou.framework.setting.operator.SettingPrefs"
//                }
//
//            }
//
//            val instance = findGetInstance()?.invoke(null)
//            if (instance == null) {
//                YLog.error(tag = TAG, msg = "Failed to get instance of SettingPrefs")
//                return
//            }
//            val isChecked = getMethod.invoke(instance) as Boolean
//            YLog.debug(tag = TAG, msg = "isTranslationChecked: $isChecked")
//
//            provider?.player?.setDisplayTranslation(isChecked)
//        }
//    }

}