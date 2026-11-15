/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

plugins {
    id("lyricon.android.application")
    id("lyricon.libxposed")
}

android {
    namespace = "io.github.proify.lyricon.cmprovider"

    defaultConfig {
        applicationId = "io.github.proify.lyricon.cmprovider"
        minSdk = 28
        versionCode = 8
        versionName = "1.1.0-beta1"
    }
}

dependencies {
    implementation(project(":common:base:extensions-kt"))
    implementation(project(":common:codec:lrckit"))
    implementation(project(":common:codec:yrckit"))
    implementation(libs.dexkit)
}
