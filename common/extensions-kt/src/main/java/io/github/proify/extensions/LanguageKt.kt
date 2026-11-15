/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.extensions

import java.util.Locale

fun Locale.isChinese(): Boolean {
    return language.equals("zh", ignoreCase = true)
}