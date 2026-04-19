/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.kgprovider.xposed.kugou

open class KuGou : KuGouBase() {
    
    override fun shouldHookProcess(): Boolean {
        return processName.endsWith(":support") || processName.endsWith(".support")
    }
}
