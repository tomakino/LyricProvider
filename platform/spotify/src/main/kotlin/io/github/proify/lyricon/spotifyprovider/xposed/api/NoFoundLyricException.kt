/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.spotifyprovider.xposed.api

class NoFoundLyricException(val id: String, msg: String) : RuntimeException(msg)