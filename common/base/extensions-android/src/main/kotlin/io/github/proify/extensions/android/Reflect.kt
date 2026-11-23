/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.extensions.android

import com.highcapable.kavaref.extension.isStatic
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * 轻量级反射工具.
 *
 * 特性:
 * - 实例 / 静态 字段读写、方法调用、构造器
 * - 沿继承链与接口查找成员
 * - 重载方法按匹配精确度择优（避免 `Object` 参数误匹配更具体重载）
 * - 命中与未命中均缓存
 *
 * 注意:
 * - 反射不会自动做数值 widening/窄化（例如传入 [Int] 无法匹配参数为 `long` 的方法），
 *   这符合 [Method.invoke] 自身的语义；如需自动转换请先在调用方完成。
 * - 所有 API 不抛异常：以 `xxxOrNull` 或 [Boolean] 返回。
 */
object Reflect {

    /** 缓存未命中的哨兵（ConcurrentHashMap 不接受 null value）。 */
    private val NOT_FOUND = Any()

    /** 参数类型 key 中表示 null 的占位符，不会与任何二进制类名冲突。 */
    private const val NULL_ARG = "<null>"

    /** 匹配成本达到此值即视为不可用。 */
    private const val UNMATCHABLE = 10_000

    private val fieldCache = ConcurrentHashMap<String, Any>()
    private val methodCache = ConcurrentHashMap<String, Any>()
    private val ctorCache = ConcurrentHashMap<String, Any>()
    private val methodListCache = ConcurrentHashMap<String, List<Method>>()

    // ---------------------------------------------------------------- 字段

    /**
     * 读取实例字段.
     *
     * @param instance 目标对象（若字段为 static 也能读取）
     * @return 字段值；对象为 null / 字段不存在 / 访问失败时为 null
     */
    fun fieldOrNull(instance: Any?, name: String): Any? {
        val target = instance ?: return null
        val field = fieldOf(target.javaClass, name, requireStatic = false) ?: return null
        return readField(field, target)
    }

    /** 读取静态字段. */
    fun staticFieldOrNull(type: Class<*>, name: String): Any? {
        val field = fieldOf(type, name, requireStatic = true) ?: return null
        return readField(field, null)
    }

    /** 写入实例字段；成功返回 true. */
    fun setField(instance: Any?, name: String, value: Any?): Boolean {
        val target = instance ?: return false
        val field = fieldOf(target.javaClass, name, requireStatic = false) ?: return false
        return writeField(field, target, value)
    }

    /** 写入静态字段；成功返回 true. */
    fun setStaticField(type: Class<*>, name: String, value: Any?): Boolean {
        val field = fieldOf(type, name, requireStatic = true) ?: return false
        return writeField(field, null, value)
    }

    // ---------------------------------------------------------------- 方法

    /**
     * 调用实例方法.
     *
     * @param instance 目标对象（若方法是 static 也能调用）
     * @return 方法返回值；对象为 null / 方法不存在 / 调用失败时为 null
     */
    fun callOrNull(instance: Any?, name: String, vararg args: Any?): Any? {
        val target = instance ?: return null
        val method = methodOf(target.javaClass, name, args, requireStatic = false) ?: return null
        return invokeMethod(method, target, args)
    }

    /** 调用静态方法. */
    fun staticCallOrNull(type: Class<*>, name: String, vararg args: Any?): Any? {
        val method = methodOf(type, name, args, requireStatic = true) ?: return null
        return invokeMethod(method, null, args)
    }

    // ---------------------------------------------------------------- 构造器

    /** 创建实例. */
    fun newInstanceOrNull(type: Class<*>, vararg args: Any?): Any? {
        val ctor = ctorOf(type, args) ?: return null
        return runCatching {
            ctor.isAccessible = true
            ctor.newInstance(*args)
        }.getOrNull()
    }

    // ---------------------------------------------------------------- 缓存

    /** 清空内部缓存（一般无需手动调用）. */
    fun clearCache() {
        fieldCache.clear()
        methodCache.clear()
        ctorCache.clear()
        methodListCache.clear()
    }

    // ---------------------------------------------------------------- 内部实现

    private fun readField(field: Field, target: Any?): Any? = runCatching {
        field.isAccessible = true
        field.get(target)
    }.getOrNull()

    private fun writeField(field: Field, target: Any?, value: Any?): Boolean = runCatching {
        field.isAccessible = true
        field.set(target, value)
    }.isSuccess

    private fun invokeMethod(method: Method, target: Any?, args: Array<out Any?>): Any? =
        runCatching {
            method.isAccessible = true
            method.invoke(target, *args)
        }.getOrNull()

    private fun fieldOf(type: Class<*>, name: String, requireStatic: Boolean): Field? {
        val key = buildString {
            append("F#").append(type.name).append('#').append(name)
            append('#').append(if (requireStatic) 1 else 0)
        }
        fieldCache[key]?.let { cached -> return if (cached === NOT_FOUND) null else cached as Field }

        var current: Class<*>? = type
        while (current != null) {
            val found = current.declaredFields.firstOrNull {
                it.name == name && (!requireStatic || it.isStatic)
            }
            if (found != null) {
                fieldCache[key] = found
                return found
            }
            current = current.superclass
        }
        fieldCache[key] = NOT_FOUND
        return null
    }

    private fun methodOf(
        type: Class<*>,
        name: String,
        args: Array<out Any?>,
        requireStatic: Boolean
    ): Method? {
        val key = buildString {
            append("M#").append(type.name).append('#').append(name)
            append('#').append(if (requireStatic) 1 else 0)
            args.forEach { append('#').append(it?.javaClass?.name ?: NULL_ARG) }
        }
        methodCache[key]?.let { cached -> return if (cached === NOT_FOUND) null else cached as Method }

        val best = candidatesOf(type, name)
            .asSequence()
            .filter { it.parameterTypes.size == args.size }
            .filter { !requireStatic || it.isStatic }
            .map { it to matchCost(it.parameterTypes, args) }
            .filter { it.second < UNMATCHABLE }
            .minByOrNull { it.second }
            ?.first

        methodCache[key] = best ?: NOT_FOUND
        return best
    }

    private fun ctorOf(type: Class<*>, args: Array<out Any?>): Constructor<*>? {
        val key = buildString {
            append("C#").append(type.name)
            args.forEach { append('#').append(it?.javaClass?.name ?: NULL_ARG) }
        }
        ctorCache[key]?.let { cached -> return if (cached === NOT_FOUND) null else cached as Constructor<*> }

        val best = runCatching {
            type.declaredConstructors
                .asSequence()
                .filter { it.parameterTypes.size == args.size }
                .map { it to matchCost(it.parameterTypes, args) }
                .filter { it.second < UNMATCHABLE }
                .minByOrNull { it.second }
                ?.first
        }.getOrNull()

        ctorCache[key] = best ?: NOT_FOUND
        return best
    }

    /** 收集类（含 superclass 链与接口）中名为 [name] 的所有方法。 */
    private fun collectMethods(type: Class<*>, name: String): List<Method> {
        val methods = LinkedHashSet<Method>()
        var current: Class<*>? = type
        while (current != null) {
            current.declaredMethods.filterTo(methods) { it.name == name }
            current = current.superclass
        }
        // 接口方法（包括默认方法 / 未被实现类覆盖的抽象方法）
        runCatching {
            methods.addAll(type.methods.filter { it.name == name })
        }
        return methods.toList()
    }

    /** 按 type+name 缓存候选方法列表，避免每次重载查找都重新收集。 */
    private fun candidatesOf(type: Class<*>, name: String): List<Method> =
        methodListCache.getOrPut("L#${type.name}#$name") { collectMethods(type, name) }

    /** 计算参数匹配成本：0=精确、10=父类/接口、100=Object、不可用=UNMATCHABLE。 */
    private fun matchCost(paramTypes: Array<Class<*>>, args: Array<out Any?>): Int {
        if (paramTypes.size != args.size) return UNMATCHABLE
        var cost = 0
        for (i in paramTypes.indices) {
            val paramType = paramTypes[i]
            val arg = args[i]
            if (arg == null) {
                if (paramType.isPrimitive) return UNMATCHABLE
                continue
            }
            val boxedParam = paramType.boxed()
            val argType = arg.javaClass
            cost += when {
                boxedParam == argType -> 0
                boxedParam == Any::class.java -> 100
                boxedParam.isAssignableFrom(argType) -> 10
                else -> return UNMATCHABLE
            }
        }
        return cost
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
        Void.TYPE -> Void::class.java
        else -> this
    }
}