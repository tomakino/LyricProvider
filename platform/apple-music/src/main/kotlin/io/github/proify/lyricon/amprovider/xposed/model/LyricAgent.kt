/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.amprovider.xposed.model

import kotlinx.serialization.Serializable

@Serializable
data class LyricAgent(
    var nameTypes: IntArray = intArrayOf(),
    var nameTypeNames: Array<String> = arrayOf(),
    var type: Long = 0,
    var typeName: String? = null,
    var id: String? = null
) {

    companion object {
        /** 根据 nameTypes 得到对应的类型名称数组，未知类型补空串。 */
        fun nameTypesToNames(nameTypes: IntArray): Array<String> =
            nameTypes.map { nameTypeOf(it)?.typeName ?: "" }.toTypedArray()

        fun nameTypeOf(nameType: Int): NameType? =
            NameType.entries.firstOrNull { it.type == nameType }

        fun typeOf(type: Long): Type? =
            Type.entries.firstOrNull { it.type == type }
    }

    enum class NameType(val typeName: String, val type: Int) {
        NONE("None", 0),
        FULL("Full", 1),
        FAMILY("Family", 2),
        GIVEN("Given", 3),
        ALIAS("Alias", 4),
        OTHER("Other", 5)
    }

    enum class Type(val typeName: String, val type: Long) {
        NONE("None", 0),
        PERSON("Person", 1),
        CHARACTER("Character", 2),
        GROUP("Group", 3),
        ORGANIZATION("Organization", 4),
        OTHER("Other", 5)
    }

    // 数组字段无法被 data class 默认 equals 正确比较，这里手动实现。
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LyricAgent) return false

        return type == other.type
                && nameTypes.contentEquals(other.nameTypes)
                && nameTypeNames.contentEquals(other.nameTypeNames)
                && typeName == other.typeName
                && id == other.id
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + nameTypes.contentHashCode()
        result = 31 * result + nameTypeNames.contentHashCode()
        result = 31 * result + (typeName?.hashCode() ?: 0)
        result = 31 * result + (id?.hashCode() ?: 0)
        return result
    }
}
