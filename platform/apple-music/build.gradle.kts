plugins {
    id("lyricon.android.application")
    id("lyricon.yukihook")
}

android {
    namespace = "io.github.proify.lyricon.amprovider"

    defaultConfig {
        applicationId = "io.github.proify.lyricon.amprovider"
        versionCode = 7
        versionName = "1.0.31"
    }
}

dependencies {
    implementation(project(":common:base:extensions-android"))
    implementation(project(":common:codec:amkit-download"))
    implementation(project(":common:codec:amkit-parser"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
}
