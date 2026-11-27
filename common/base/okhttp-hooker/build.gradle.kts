/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

plugins {
    id("lyricon.android.library")
}

android {
    namespace = "io.github.proify.lyricon.provider.okhttp_hooker"
}

dependencies {
    api(project(":common:base:dexkit"))
    api(project(":common:base:extensions-kt"))
}
