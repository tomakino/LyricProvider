/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:OptIn(DexKitExperimentalApi::class)

package io.github.proify.lyricon.dexkit

import android.util.Log
import org.luckypray.dexkit.DexKitCacheBridge
import org.luckypray.dexkit.annotations.DexKitExperimentalApi
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/** 按 appTag 分目录、带进程内内存缓存的 DexKit 查询结果存储。 */
internal class DexKitFileCache(private val rootDir: File) : DexKitCacheBridge.Cache {

    private val lock = Any()
    private val values = ConcurrentHashMap<String, String>()
    private val lists = ConcurrentHashMap<String, List<String>>()
    private val index = LinkedHashSet<String>()

    private var appTag: String? = null
    private var indexLoaded = false

    /** 绑定当前命名空间；切换命名空间时丢弃内存缓存。 */
    fun bind(appTag: String) {
        synchronized(lock) {
            val target = appTag.ifBlank { UNBOUND_TAG }
            if (this.appTag == target) return
            this.appTag = target
            clearMemory()
        }
    }

    override fun getString(key: String, default: String?): String? {
        values[key]?.let { return it }

        val value = readRaw(key) ?: return default
        if (value.isEmpty()) {
            Log.w(TAG, "empty cache entry, drop: $key")
            deleteQuietly(fileOf(key))
            return default
        }

        values[key] = value
        synchronized(lock) { if (indexLoaded) index.add(key) }
        return value
    }

    override fun putString(key: String, value: String) {
        if (value.isEmpty()) return
        values[key] = value
        synchronized(lock) { if (indexLoaded) index.add(key) }
        write(key, value)
    }

    override fun getStringList(key: String, default: List<String>?): List<String>? {
        lists[key]?.let { return it }

        val raw = readRaw(key) ?: return default
        val value = if (raw.isEmpty()) emptyList() else raw.split(SEPARATOR)
        lists[key] = value
        return value
    }

    override fun putStringList(key: String, value: List<String>) {
        lists[key] = value
        synchronized(lock) { if (indexLoaded) index.add(key) }
        write(key, value.joinToString(SEPARATOR))
    }

    override fun remove(key: String) {
        values.remove(key)
        lists.remove(key)
        synchronized(lock) {
            index.remove(key)
            deleteQuietly(fileOf(key))
        }
    }

    override fun getAllKeys(): Collection<String> {
        synchronized(lock) {
            if (!indexLoaded) {
                try {
                    dirOf().list()?.forEach { name -> decodeName(name)?.let(index::add) }
                } catch (e: Exception) {
                    Log.w(TAG, "load index failed", e)
                }
                indexLoaded = true
            }
            return index.toList()
        }
    }

    override fun clearAll() {
        synchronized(lock) {
            clearMemory()
            try {
                rootDir.list()?.forEach { name -> deleteQuietly(File(rootDir, name)) }
            } catch (e: Exception) {
                Log.w(TAG, "clearAll failed", e)
            }
        }
    }

    /** 只保留指定 appTag 的目录，其余目录与根目录残留文件一并删除。 */
    fun retainOnly(appTag: String) {
        val keep = encodeName(appTag.ifBlank { UNBOUND_TAG })
        synchronized(lock) {
            clearMemory()
            try {
                rootDir.list()?.forEach { name ->
                    if (name != keep) deleteQuietly(File(rootDir, name))
                }
            } catch (e: Exception) {
                Log.w(TAG, "retainOnly failed", e)
            }
        }
    }

    private fun readRaw(key: String): String? {
        val file = fileOf(key)
        if (!file.isFile) return null
        return try {
            file.readText()
        } catch (e: Exception) {
            Log.w(TAG, "read failed: $key", e)
            null
        }
    }

    private fun write(key: String, value: String) {
        synchronized(lock) {
            try {
                val dir = dirOf()
                if (!dir.isDirectory && !dir.mkdirs()) {
                    Log.w(TAG, "create cache dir failed: ${dir.absolutePath}")
                    return
                }

                val target = File(dir, encodeName(key))
                val temp = File(dir, target.name + TEMP_SUFFIX)
                temp.writeText(value)
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (e: Exception) {
                Log.w(TAG, "write failed: $key", e)
            }
        }
    }

    private fun deleteQuietly(file: File) {
        try {
            if (file.exists() && !file.deleteRecursively()) {
                Log.w(TAG, "delete failed: ${file.absolutePath}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "delete failed: ${file.absolutePath}", e)
        }
    }

    private fun clearMemory() {
        values.clear()
        lists.clear()
        index.clear()
        indexLoaded = false
    }

    private fun dirOf(): File = File(rootDir, encodeName(appTag ?: UNBOUND_TAG))

    private fun fileOf(key: String): File = File(dirOf(), encodeName(key))

    private fun encodeName(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())

    private fun decodeName(name: String): String? =
        try {
            String(Base64.getUrlDecoder().decode(name))
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "invalid cache file name: $name", e)
            null
        }

    private companion object {
        const val TAG = "DexKitFileCache"
        const val SEPARATOR = "\u0000"
        const val TEMP_SUFFIX = ".tmp"
        const val UNBOUND_TAG = "unbound"
    }
}
