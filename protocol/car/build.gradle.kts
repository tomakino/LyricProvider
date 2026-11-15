/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

plugins {
    id("lyricon.android.library")
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.github.proify.lyricon.library.carprovider"
}

dependencies {
    implementation(project(":common:base:extensions-android"))
    implementation(libs.lyricon.provider)
    ksp(libs.yukihookapi.ksp.xposed)
}
