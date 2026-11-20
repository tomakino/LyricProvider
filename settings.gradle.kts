/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("UnstableApiUsage")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        mavenLocal()
        // 本地走阿里云镜像；GitHub Actions 在海外，直连官方源。
        if (System.getenv("CI").isNullOrEmpty()) {
            maven("https://maven.aliyun.com/repository/public")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/gradle-plugin")
        }
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        google()
        gradlePluginPortal()
        maven { url = uri("https://api.xposed.info/") }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        if (System.getenv("CI").isNullOrEmpty()) {
            maven("https://maven.aliyun.com/repository/public")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/gradle-plugin")
        }
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        google()
        maven { url = uri("https://api.xposed.info/") }
    }
}

rootProject.name = "LyricProvider"

/**
 * 自动 include 某个分组目录下的所有子模块。
 * 只收目录，且必须存在 build.gradle.kts，避免把 build/ 等杂物当成模块。
 */
fun includeGroup(group: String) {
    val dir = file(group)
    if (!dir.isDirectory) return
    dir.listFiles()
        ?.filter { it.isDirectory && file("$group/${it.name}/build.gradle.kts").isFile }
        ?.sortedBy { it.name }
        ?.forEach { include(":$group:${it.name}") }
}

fun includeTree(root: String) {
    fun visit(rel: String) {
        val dir = file(rel)
        if (!dir.isDirectory) return
        dir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name }
            ?.forEach { sub ->
                val child = "$rel/${sub.name}"
                if (file("$child/build.gradle.kts").isFile) {
                    include(":${child.replace('/', ':')}")
                } else {
                    visit(child)
                }
            }
    }
    visit(root)
}

includeGroup("platform")
includeGroup("universal")
includeGroup("protocol")
includeTree("common")