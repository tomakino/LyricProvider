/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.kgprovider.xposed.kugou

import android.util.Log
import java.lang.reflect.Method
import java.lang.reflect.Field

object KuGouVersionDetector {
    private const val TAG = "KuGouVersionDetector"
    
    data class VersionInfo(
        val hasImgUrlSetter: Boolean = false,
        val hasImgUrlGetter: Boolean = false,
        val imgUrlSetterName: String? = null,
        val imgUrlGetterName: String? = null,
        val imgUrlFieldName: String? = null,
        val detectedVersion: String = "unknown"
    )
    
    fun detectVersion(classLoader: ClassLoader?): VersionInfo {
        Log.i(TAG, "=== Detecting KuGou version ===")
        
        val kgMusicClass = try {
            Class.forName("com.kugou.android.common.entity.KGMusic", false, classLoader)
        } catch (e: Exception) {
            Log.e(TAG, "KGMusic class not found: ${e.message}")
            return VersionInfo()
        }
        
        Log.i(TAG, "Found KGMusic class: ${kgMusicClass.name}")
        
        var hasImgUrlSetter = false
        var hasImgUrlGetter = false
        var imgUrlSetterName: String? = null
        var imgUrlGetterName: String? = null
        var imgUrlFieldName: String? = null
        
        kgMusicClass.declaredMethods.forEach { method ->
            val params = method.parameterTypes.map { it.simpleName }
            Log.d(TAG, "Method: ${method.name}(${params.joinToString()}) -> ${method.returnType.simpleName}")
            
            if (method.parameterCount == 1 && method.parameterTypes[0] == String::class.java) {
                val name = method.name.lowercase()
                if (name.contains("img") || name.contains("url") || name.contains("cover") || name == "t") {
                    hasImgUrlSetter = true
                    imgUrlSetterName = method.name
                    Log.i(TAG, "Found potential imgUrl setter: ${method.name}(String)")
                }
            }
            
            if (method.parameterCount == 0 && method.returnType == String::class.java) {
                val name = method.name.lowercase()
                if (name.contains("img") || name.contains("url") || name.contains("cover") || name == "ad") {
                    hasImgUrlGetter = true
                    imgUrlGetterName = method.name
                    Log.i(TAG, "Found potential imgUrl getter: ${method.name}() -> String")
                }
            }
        }
        
        kgMusicClass.declaredFields.forEach { field ->
            if (field.type == String::class.java) {
                val name = field.name.lowercase()
                if (name.contains("img") || name.contains("url") || name.contains("cover") || name == "t") {
                    imgUrlFieldName = field.name
                    Log.i(TAG, "Found potential imgUrl field: ${field.name}")
                }
            }
        }
        
        val version = when {
            hasImgUrlSetter && hasImgUrlGetter -> "full"
            hasImgUrlSetter -> "setter_only"
            hasImgUrlGetter -> "getter_only"
            imgUrlFieldName != null -> "field_only"
            else -> "minimal"
        }
        
        Log.i(TAG, "Detected version: $version")
        
        return VersionInfo(
            hasImgUrlSetter = hasImgUrlSetter,
            hasImgUrlGetter = hasImgUrlGetter,
            imgUrlSetterName = imgUrlSetterName,
            imgUrlGetterName = imgUrlGetterName,
            imgUrlFieldName = imgUrlFieldName,
            detectedVersion = version
        )
    }
}
