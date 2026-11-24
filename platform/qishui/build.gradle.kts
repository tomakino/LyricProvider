/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

plugins {
    id("lyricon.android.application")
    id("lyricon.yukihook")
}

android {
    namespace = "io.github.proify.lyricon.qishuiprovider"

    defaultConfig {
        applicationId = "io.github.proify.lyricon.qishuiprovider"
        versionCode = 2
        versionName = "1.1.0"
    }
}

dependencies {
    implementation(project(":common:base:extensions-android"))
    implementation(project(":common:codec:lrckit"))
    implementation(project(":common:codec:qishuikit"))
}
