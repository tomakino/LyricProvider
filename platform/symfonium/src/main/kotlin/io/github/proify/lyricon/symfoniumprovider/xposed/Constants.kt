/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.symfoniumprovider.xposed

object Constants {
    const val PROVIDER_PACKAGE_NAME: String = "io.github.proify.lyricon.symfoniumprovider"

    val ICON: String =
        """
      <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">
  <g transform="translate(2.08, 2) scale(0.04)">
    <path fill="#E22728" d="M71,0h334c26,0 48,30 49,64 1,35 -20,62 -46,61s-47,-29 -48,-64c0,-11 2,-22 6,-31L157,30c-35,0 -39,252 -2,252h86c26,0 47,28 47,63s-21,64 -47,64c-26,-1 -48,-29 -48,-64 0,-12 2,-22 7,-32L67,313C29,313 34,0 71,0zM429,500L95,500c-26,0 -48,-30 -49,-64s20,-62 46,-61 47,29 48,64c0,11 -2,22 -6,31h209c35,0 39,-252 2,-252h-86c-26,0 -47,-28 -47,-63s21,-64 47,-64c26,1 48,29 48,64 0,12 -2,22 -7,32h133c38,0 33,313 -4,313z" />
  </g>
</svg>    
        """.trimIndent()
}