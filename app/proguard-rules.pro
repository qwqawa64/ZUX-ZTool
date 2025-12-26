# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# 1. 保持 Xposed 库的接口不被混淆
-keep interface de.robv.android.xposed.** { *; }
# 2. 入口类不被混淆/重命名
-keep class com.qimian233.ztool.hook.HookInit { *; }
# 3. 如果 HookManager 中使用了反射查找自身的方法，也建议保持
-keep class com.qimian233.ztool.hook.base.HookManager { *; }
# 4. 保持 HiddenApiBypass 库
-keep class org.lsposed.hiddenapibypass.** { *; }
# 5. 一般性的反射保护
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses