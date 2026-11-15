package io.github.proify.extensions

import java.security.MessageDigest

private val md5ThreadLocal = ThreadLocal.withInitial { MessageDigest.getInstance("MD5") }

/**
 * 计算字符串的 MD5 值
 */
fun String.md5(): String {
    val md = md5ThreadLocal.get()
    md.reset()
    val bytes = md.digest(this.toByteArray())
    val sb = StringBuilder(bytes.size * 2)
    for (b in bytes) {
        val hex = (b.toInt() and 0xFF).toString(16)
        if (hex.length == 1) sb.append('0')
        sb.append(hex)
    }
    return sb.toString()
}