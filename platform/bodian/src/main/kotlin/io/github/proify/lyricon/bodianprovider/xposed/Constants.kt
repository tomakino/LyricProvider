/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.bodianprovider.xposed

object Constants {

    /** 本模块包名 */
    const val PROVIDER_PACKAGE_NAME: String = "io.github.proify.lyricon.bodianprovider"

    /** 波点音乐（QQ 音乐简洁版）包名 */
    const val MUSIC_PACKAGE_NAME: String = "cn.wenyu.bodian"

    /**
     * 模块图标（原创绘制的"波点"意象：声波 + 圆点，不使用官方商标素材）
     */
    val ICON: String = """
        <svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64">
          <circle cx="32" cy="32" r="30" fill="#111318"/>
          <g fill="#4CD5A0">
            <circle cx="16" cy="32" r="3.2"/>
            <circle cx="26" cy="32" r="3.2"/>
            <circle cx="36" cy="32" r="3.2"/>
            <circle cx="46" cy="32" r="3.2"/>
          </g>
          <path d="M12 32c4-12 8-12 12 0s8 12 12 0 8-12 12 0"
                fill="none" stroke="#4CD5A0" stroke-width="3.2"
                stroke-linecap="round" stroke-linejoin="round" opacity="0.55"/>
        </svg>
    """.trimIndent()
}
