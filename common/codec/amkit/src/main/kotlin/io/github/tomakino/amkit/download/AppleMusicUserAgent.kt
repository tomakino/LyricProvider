/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.tomakino.amkit.download

import io.github.tomakino.amkit.download.AppleMusicUserAgent.NEUTRAL_DEVICE_MODEL
import io.github.tomakino.amkit.download.AppleMusicUserAgent.generate


/**
 * Apple Music 客户端 User-Agent 生成器。
 *
 * ## 为什么需要它
 *
 * 接口会校验请求来源：UA 不是客户端格式时一律返回 404，而这个 404 与
 * 「缺少 Cookie」时的报错完全一致，极易被误判成凭证问题（已实测确认）。
 *
 * ## 设计取舍
 *
 * 生成值**不含任何真实设备标识** —— 机型固定为中性占位 [NEUTRAL_DEVICE_MODEL]，
 * 而不是从抓包里看到的某个具体型号。硬编码他人设备既无必要，也会随着
 * 该型号被服务端标记而失效；真要用真实机型，应由调用方自己传进来。
 *
 * 所有取值均为常量，[generate] 是纯函数：**同样的输入永远得到同样的输出**。
 * 这一点很重要 —— 客户端指纹忽变（例如每次随机机型）本身就会成为异常信号。
 */
object AppleMusicUserAgent {

    /** 客户端版本号。 */
    const val DEFAULT_APP_VERSION = "6.5.2"

    /** Android 版本号。 */
    const val DEFAULT_ANDROID_RELEASE = "16"

    /** 构建号。 */
    const val DEFAULT_BUILD_NUMBER = "1586"

    /** 中性机型占位，不冒充任何具体设备。 */
    const val NEUTRAL_DEVICE_MODEL = "android"

    /** 尾部的客户端类型标识。 */
    const val DEFAULT_CLIENT_TYPE = "66"

    /**
     * 生成客户端 UA。
     *
     * @param deviceModel 设备型号。默认中性；需要贴近真实设备时由调用方传入
     *   （Android 上通常是 `Build.MODEL`）。
     * @throws IllegalArgumentException [deviceModel] 为空时抛出。
     */
    fun generate(
        appVersion: String = DEFAULT_APP_VERSION,
        androidRelease: String = DEFAULT_ANDROID_RELEASE,
        deviceModel: String = NEUTRAL_DEVICE_MODEL,
        buildNumber: String = DEFAULT_BUILD_NUMBER,
        clientType: String = DEFAULT_CLIENT_TYPE,
    ): String {
        require(deviceModel.isNotBlank()) { "deviceModel 不能为空" }
        return "Music/$appVersion Android/$androidRelease " +
                "model/$deviceModel build/$buildNumber (dt:$clientType)"
    }

    /**
     * 默认 UA，供 [AppleMusicLyricsDownloader] 直接使用。
     *
     * 用 `val`（而非 `get() = generate()`）保证**整个进程内只计算一次**，
     * 避免同一客户端在不同请求间出现不一致的指纹。
     */
    val default: String = generate()
}
