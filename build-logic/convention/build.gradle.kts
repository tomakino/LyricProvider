plugins {
    `kotlin-dsl`
}

group = "io.github.proify.lyricon.buildlogic"

dependencies {
    compileOnly(libs.gradle.android.plugin)
    compileOnly(libs.gradle.kotlin.plugin)
    compileOnly(libs.gradle.ksp.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "lyricon.android.application"
            implementationClass = "lyricon.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "lyricon.android.library"
            implementationClass = "lyricon.AndroidLibraryConventionPlugin"
        }
        register("yukiHook") {
            id = "lyricon.yukihook"
            implementationClass = "lyricon.YukiHookConventionPlugin"
        }
        register("libXposed") {
            id = "lyricon.libxposed"
            implementationClass = "lyricon.LibXposedConventionPlugin"
        }
    }
}
