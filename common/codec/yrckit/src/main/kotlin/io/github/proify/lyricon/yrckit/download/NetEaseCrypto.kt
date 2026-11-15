/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.yrckit.download

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

internal object NetEaseCrypto {

    private const val E_API_KEY = "e82ckenh8dichen8"
    private const val E_API_FORMAT = "%s-36cd479b6b5-%s-36cd479b6b5-%s"
    private const val E_API_SALT = "nobody%suse%smd5forencrypt"

    /**
     * EApi 加密实现
     * @param url 请求路由，例如 "/api/v1/playlist/detail"
     * @param jsonData 业务参数的 JSON 字符串
     */
    fun eApiEncrypt(url: String, jsonData: String): Map<String, String> {
        // 1. 替换路由中的 eapi 为 api
        val modifiedUrl = url.replace("eapi", "api")

        // 2. 生成摘要 (Digest)
        val digest = hexDigest(String.format(E_API_SALT, modifiedUrl, jsonData))

        // 3. 构建待加密的文本内容
        val text = String.format(E_API_FORMAT, modifiedUrl, jsonData, digest)

        // 4. AES ECB 加密并转为大写 Hex
        val encryptedData = aesEncryptEcb(text, E_API_KEY)

        return mapOf("params" to encryptedData.uppercase())
    }

    private fun hexDigest(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    @Suppress("GetInstance")
    private fun aesEncryptEcb(text: String, key: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        val keySpec = SecretKeySpec(key.toByteArray(), "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)

        val encryptedBytes = cipher.doFinal(text.toByteArray())
        return encryptedBytes.joinToString("") { "%02x".format(it) }
    }
}