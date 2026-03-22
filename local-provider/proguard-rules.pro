# 保留 Xposed 入口和 Hook 类
-keep class io.github.proify.lyricon.localprovider.xposed.HookEntry
-keep class io.github.proify.lyricon.localprovider.xposed.LocalProvider

# 保留 YukiHookAPI 及其依赖
-keep class com.highcapable.yukihookapi.** { *; }
-keep class com.highcapable.kavaref.** { *; }

# 保留 TagLib（内嵌歌词需要）
-keep class com.kyant.taglib.** { *; }

# 保留 Kotlin 反射（可能需要）
-keep class kotlin.reflect.** { *; }
-keep class kotlin.Metadata { *; }

# 保留泛型和注解信息（防止反射失效）
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# 移除所有 Log 调用（减小体积）
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# 移除 Kotlin 内联检查（可选）
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
}

# 忽略警告（如反射相关）
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn sun.misc.**

# 保留行号信息（便于调试）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile