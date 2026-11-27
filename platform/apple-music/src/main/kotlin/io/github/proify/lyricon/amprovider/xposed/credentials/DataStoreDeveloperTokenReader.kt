/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.credentials

import android.content.Context
import com.highcapable.yukihookapi.hook.log.YLog
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 从 DataStore + AndroidKeyStore 读取 developerToken（7.0.0 起）。 */
class DataStoreDeveloperTokenReader : DeveloperTokenReader {

    companion object {
        private const val TOKENS_FILE = "datastore/encrypted_tokens.preferences_pb"
        private const val TOKEN_STORE_KEY = "music_token_store_key"
        private const val API_TOKEN_KEY = "key_api_token_musicAndroid"
        private const val PREF_CIPHER = "__cipher"
        private const val PREF_IV = "__iv"
        private const val WIRE_LENGTH = 2
    }

    override fun read(context: Context): String? {
        val file = File(context.filesDir, TOKENS_FILE)
        if (!file.isFile) {
            YLog.info("$TOKENS_FILE 不存在")
            return null
        }

        val key = loadKeyStoreKey() ?: run {
            YLog.info("KeyStore 缺少 $TOKEN_STORE_KEY")
            return null
        }

        val bytes = runCatching { file.readBytes() }.getOrElse {
            YLog.info("读取 $TOKENS_FILE 失败", it)
            return null
        }
        val entries = runCatching { parseDataStore(bytes) }.getOrElse {
            YLog.info("解析 $TOKENS_FILE 失败", it)
            return null
        }

        val cipher = entries[API_TOKEN_KEY + PREF_CIPHER] ?: run {
            YLog.info("$TOKENS_FILE 缺少 ${API_TOKEN_KEY}__cipher")
            return null
        }
        val iv = entries[API_TOKEN_KEY + PREF_IV] ?: run {
            YLog.info("$TOKENS_FILE 缺少 ${API_TOKEN_KEY}__iv")
            return null
        }

        return runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(128, android.util.Base64.decode(iv, 0)),
            )
            String(c.doFinal(android.util.Base64.decode(cipher, 0)), Charsets.UTF_8)
        }.getOrElse {
            YLog.info("解密 developerToken 失败", it)
            null
        }
    }

    private fun loadKeyStoreKey(): SecretKey? = runCatching {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val entry = keyStore.getEntry(TOKEN_STORE_KEY, null)
        (entry as? KeyStore.SecretKeyEntry)?.secretKey
    }.getOrElse {
        YLog.info("读取 KeyStore $TOKEN_STORE_KEY 失败", it)
        null
    }

    /** 解析 DataStore protobuf：entry{ key:1:string, value:2:message{ 5:string } }。 */
    private fun parseDataStore(bytes: ByteArray): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val buffer = ByteBuffer.wrap(bytes)

        while (buffer.hasRemaining()) {
            val tag = readVarint(buffer)
            val wire = (tag and 0x7).toInt()
            if (wire != WIRE_LENGTH) {
                skip(buffer, wire)
                continue
            }
            val chunk = readBytes(buffer)
            if ((tag ushr 3).toInt() != 1) continue

            val entry = ByteBuffer.wrap(chunk)
            var name: String? = null
            var value: String? = null
            while (entry.hasRemaining()) {
                val entryTag = readVarint(entry)
                val entryWire = (entryTag and 0x7).toInt()
                if (entryWire != WIRE_LENGTH) {
                    skip(entry, entryWire)
                    continue
                }
                val part = readBytes(entry)
                when ((entryTag ushr 3).toInt()) {
                    1 -> name = String(part, Charsets.UTF_8)
                    2 -> value = parseValue(part)
                }
            }
            if (name != null && value != null) result[name] = value
        }
        return result
    }

    private fun parseValue(bytes: ByteArray): String? {
        val buffer = ByteBuffer.wrap(bytes)
        while (buffer.hasRemaining()) {
            val tag = readVarint(buffer)
            val wire = (tag and 0x7).toInt()
            if (wire != WIRE_LENGTH) {
                skip(buffer, wire)
                continue
            }
            val part = readBytes(buffer)
            if ((tag ushr 3).toInt() == 5) return String(part, Charsets.UTF_8)
        }
        return null
    }

    private fun readVarint(buffer: ByteBuffer): Long {
        var value = 0L
        var shift = 0
        while (buffer.hasRemaining() && shift < 64) {
            val byte = buffer.get().toInt()
            value = value or ((byte and 0x7f).toLong() shl shift)
            if (byte and 0x80 == 0) break
            shift += 7
        }
        return value
    }

    private fun readBytes(buffer: ByteBuffer): ByteArray {
        val length = readVarint(buffer).toInt()
        return ByteArray(length).also { buffer.get(it) }
    }

    private fun skip(buffer: ByteBuffer, wire: Int) {
        when (wire) {
            0 -> readVarint(buffer)
            1 -> buffer.position(buffer.position() + 8)
            5 -> buffer.position(buffer.position() + 4)
            else -> error("unsupported wire $wire")
        }
    }
}