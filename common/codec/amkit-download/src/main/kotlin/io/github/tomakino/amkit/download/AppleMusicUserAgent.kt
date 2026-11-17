/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.tomakino.amkit.download

/** Apple Music 客户端 User-Agent 生成器。 */
object AppleMusicUserAgent {

    const val DEFAULT_APP_VERSION = "6.5.2"

    const val DEFAULT_ANDROID_RELEASE = "16"

    const val DEFAULT_BUILD_NUMBER = "1586"

    /** 中性机型占位，不冒充任何具体设备。 */
    const val NEUTRAL_DEVICE_MODEL = "android"

    const val DEFAULT_CLIENT_TYPE = "66"

    /**
     * 生成客户端 UA。
     *
     * @param deviceModel 设备型号
     * @throws IllegalArgumentException 设备型号为空
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

    /** 默认 UA。 */
    val default: String = generate()
}
