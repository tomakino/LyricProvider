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
    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "io.github.proify.lyricon.qishuiprovider"
        versionCode = 1
        versionName = "1.0.0"

        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_static")
                abiFilters += listOf("arm64-v8a")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs {
            // LSPosed 的 LspModuleClassLoader.findLibrary 只认 APK 里 STORED 的 so
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(project(":common:base:extensions-android"))
    implementation(project(":common:codec:lrckit"))
    implementation(project(":common:codec:qishuikit"))
}
