/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("SpellCheckingInspection", "unused")

package io.github.proify.lyricon.krckit

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.InflaterInputStream

/**
 * KRC 歌词解密工具类
 * 负责处理 KRC 文件的 XOR 解密与 Zlib 解压缩逻辑
 */
object KrcDecryptor {

    /** 解密密钥 (AS3 newkeyBytes) */
    private val DECRYPT_KEY = byteArrayOf(
        64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45,
        206.toByte(), 210.toByte(), 110, 105
    )

    /** 文件起始偏移量 */
    private const val OFFSET = 4

    /**
     * 加载器完成回调处理函数
     * 负责对传入的字节数组进行特定偏移读取、XOR 解密、Zlib 解压并转换为字符串
     *
     * @param input 原始加密字节数组 (ByteArray)
     * @return 解密并解压后的字符串，若处理失败则返回 null
     */
    @JvmStatic
    fun decrypt(input: ByteArray): String? {
        // 校验输入合法性：必须大于偏移量
        if (input.size <= OFFSET) {
            return null
        }

        return runCatching {
            // 1. 原地进行 XOR 解密，避免 copyOfRange 产生额外内存分配
            // 跳过前 4 个字节，从索引 OFFSET 开始处理
            val decryptedLength = input.size - OFFSET
            val decryptedBytes = ByteArray(decryptedLength)

            for (i in 0 until decryptedLength) {
                val key = DECRYPT_KEY[i % DECRYPT_KEY.size].toInt()
                val encryptedByte = input[i + OFFSET].toInt()
                // XOR 运算并存入新数组
                decryptedBytes[i] = (encryptedByte xor key).toByte()
            }

            // 2. 执行 Zlib 解压缩并转换为 UTF-8 字符串
            decompressAndToString(decryptedBytes)
        }.getOrNull() // 发生异常时静默返回 null，或根据需求记录日志
    }

    /**
     * 执行 Zlib 解压并将结果转为字符串
     *
     * @param compressedData 解密后的 Zlib 压缩数据
     * @return 解压后的 UTF-8 字符串
     */
    private fun decompressAndToString(compressedData: ByteArray): String {
        return ByteArrayInputStream(compressedData).use { bais ->
            InflaterInputStream(bais).use { inflaterStream ->
                ByteArrayOutputStream(compressedData.size * 2).use { baos ->
                    val buffer = ByteArray(2048)
                    var len: Int
                    while (inflaterStream.read(buffer).also { len = it } != -1) {
                        baos.write(buffer, 0, len)
                    }
                    // 将解压后的字节数组转换为 UTF-8 字符串
                    baos.toString(StandardCharsets.UTF_8.name())
                }
            }
        }
    }
}