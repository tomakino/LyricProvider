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
    namespace = "io.github.proify.lyricon.paprovider"

    defaultConfig {
        applicationId = "io.github.proify.lyricon.paprovider"
        minSdk = 28
        versionCode = 3
        versionName = "1.0.6"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(project(":common:base:extensions-android"))
    implementation(project(":common:codec:lrckit"))
    implementation(project(":common:base:cloud-lyric-engine"))
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.appcompat)
    implementation(libs.taglib)
}
