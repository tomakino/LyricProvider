/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.kgprovider.xposed.kugou

object KuGouVersionConfig {
    
    data class MethodConfig(
        val beginTimesMethods: List<String>,
        val endTimesMethods: List<String>,
        val originalTextsMethods: List<String>,
        val translationMethods: List<String>
    )
    
    data class FieldConfig(
        val beginTimesFields: List<String>,
        val endTimesFields: List<String>,
        val originalTextsFields: List<String>,
        val translationFields: List<String>
    )
    
    val METHOD_CONFIGS = listOf(
        MethodConfig(
            beginTimesMethods = listOf("setRowBeginTime", "O"),
            endTimesMethods = listOf("setRowDelayTime", "R"),
            originalTextsMethods = listOf("setWords", "i0"),
            translationMethods = listOf("setTranslateWords", "setChineseWords", "setTransliterationWords", "F", "U", "V")
        )
    )
    
    val FIELD_CONFIGS = listOf(
        FieldConfig(
            beginTimesFields = listOf("rowBeginTime", "c"),
            endTimesFields = listOf("rowDelayTime", "d"),
            originalTextsFields = listOf("words", "e"),
            translationFields = listOf("translateWords", "chineseWords", "h")
        ),
        FieldConfig(
            beginTimesFields = listOf("d"),
            endTimesFields = listOf("e"),
            originalTextsFields = listOf("f"),
            translationFields = listOf("k")
        )
    )
    
    val DEFAULT_METHOD_CONFIG = METHOD_CONFIGS.first()
    val DEFAULT_FIELD_CONFIG = FIELD_CONFIGS.first()
}
