# 保留模块所有类
-keep class io.github.proify.lyricon.kgprovider.** { *; }

# 保留 YukiHookAPI
-keep class com.highcapable.yukihookapi.** { *; }

# 保留 Xposed 入口
-keep class io.github.proify.lyricon.kgprovider.xposed.HookEntry
-keep class io.github.proify.lyricon.kgprovider.xposed.kugou.KuGouBase
-keep class io.github.proify.lyricon.kgprovider.xposed.kugou.KuGou
-keep class io.github.proify.lyricon.kgprovider.xposed.kugou.KuGouLite
-keep class io.github.proify.lyricon.kgprovider.xposed.kugou.LyricsCache
-keep class io.github.proify.lyricon.kgprovider.xposed.kugou.KuGouVersionConfig
-keep class io.github.proify.lyricon.kgprovider.xposed.kugou.KuGouVersionConfig$*

# 保留 Kotlin 元数据
-keep class kotlin.Metadata { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# 忽略警告
-dontwarn java.lang.reflect.AnnotatedType

# 保留行号信息
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
