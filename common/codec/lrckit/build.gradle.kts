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
tasks.test {
    useJUnitPlatform()
    // 自迭代样本种子：默认固定保证 CI 可复现，可用 -Plrckit.seed=xxx 扩大覆盖范围
    systemProperty("lrckit.seed", providers.gradleProperty("lrckit.seed").getOrElse(""))
}
dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.lyricon.lyric.model)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
}