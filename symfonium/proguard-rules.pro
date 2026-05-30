# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-dontwarn java.lang.reflect.AnnotatedType
-keep class io.github.proify.lyricon.symfoniumprovider.xposed.** { *; }
-repackageclasses ''
