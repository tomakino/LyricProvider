/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.extensions.android

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * 反射访问.
 */
object Reflect {

    private val fieldCache = ConcurrentHashMap<String, Field>()

    private val methodCache = ConcurrentHashMap<String, Method>()

    /**
     * 读取实例字段.
     *
     * @param instance 目标对象
     * @param name 字段名
     * @return 字段值; 对象为 null 或字段不存在时为 null
     */
    fun fieldOrNull(instance: Any?, name: String): Any? =
        instance?.let { target -> fieldOf(target.javaClass, name)?.let { get(it, target) } }

    /**
     * 调用实例方法.
     *
     * @param instance 目标对象
     * @param name 方法名
     * @param args 方法参数
     * @return 返回值; 对象为 null、方法不存在或调用失败时为 null
     */
    fun callOrNull(instance: Any?, name: String, vararg args: Any?): Any? =
        instance?.let { target -> methodOf(target.javaClass, name, args)?.let { invoke(it, target, args) } }

    private fun get(field: Field, target: Any): Any? = runCatching {
        field.isAccessible = true
        field.get(target)
    }.getOrNull()

    private fun invoke(method: Method, target: Any, args: Array<out Any?>): Any? = runCatching {
        method.isAccessible = true
        method.invoke(target, *args)
    }.getOrNull()

    private fun fieldOf(type: Class<*>, name: String): Field? {
        val key = type.name + '#' + name
        fieldCache[key]?.let { return it }
        var current: Class<*>? = type
        while (current != null) {
            current.declaredFields.firstOrNull { it.name == name }?.let {
                fieldCache[key] = it
                return it
            }
            current = current.superclass
        }
        return null
    }

    private fun methodOf(type: Class<*>, name: String, args: Array<out Any?>): Method? {
        val key = buildString {
            append(type.name).append('#').append(name)
            args.forEach { append('#').append(it?.javaClass?.canonicalName ?: "null") }
        }
        methodCache[key]?.let { return it }
        var current: Class<*>? = type
        while (current != null) {
            current.declaredMethods.firstOrNull { it.name == name && it.isApplicable(args) }?.let {
                methodCache[key] = it
                return it
            }
            current = current.superclass
        }
        return null
    }

    private fun Method.isApplicable(args: Array<out Any?>): Boolean {
        if (parameterCount != args.size) return false
        return parameterTypes.withIndex().all { (index, type) ->
            val arg = args[index]
            if (arg == null) !type.isPrimitive else type.boxed().isInstance(arg)
        }
    }

    private fun Class<*>.boxed(): Class<*> = when (this) {
        Int::class.javaPrimitiveType -> Int::class.javaObjectType
        Long::class.javaPrimitiveType -> Long::class.javaObjectType
        Boolean::class.javaPrimitiveType -> Boolean::class.javaObjectType
        Byte::class.javaPrimitiveType -> Byte::class.javaObjectType
        Short::class.javaPrimitiveType -> Short::class.javaObjectType
        Char::class.javaPrimitiveType -> Char::class.javaObjectType
        Float::class.javaPrimitiveType -> Float::class.javaObjectType
        Double::class.javaPrimitiveType -> Double::class.javaObjectType
        else -> this
    }
}
