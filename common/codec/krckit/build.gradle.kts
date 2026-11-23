/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}
dependencies {
    implementation(project(":common:base:extensions-kt"))
    implementation(project(":common:codec:lrckit"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.lyricon.lyric.model)

    implementation(libs.okhttp)
    implementation(libs.okhttp.brotli)
    testImplementation(kotlin("test"))
}