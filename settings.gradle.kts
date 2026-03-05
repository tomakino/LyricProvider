/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        mavenLocal()
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
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        google()
        maven { url = uri("https://api.xposed.info/") }
    }
}

rootProject.name = "LyricProvider"
include(":share:extensions-kt")
include(":share:extensions-android")

include(":share:qrckit")
include(":share:lrckit")
include(":share:yrckit")
include(":share:cloudlyric")
include(":share:meizu-provider")
include(":share:car-provider")

include(":cloud-provider")
include(":meizu-provider")
include(":car-provider")

include(":apple-music")
include(":163-music")
include(":qq-music")
include(":qq-music-hd")
include(":kugou-music")
include(":kuwo-music")
include(":spotify-music")
include(":lx-music")
include(":poweramp-music")
include(":salt-player-music")
include(":qishui-music")