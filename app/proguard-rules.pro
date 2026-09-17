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
# 1. Keep Xposed library interfaces from obfuscation
-keep interface de.robv.android.xposed.** { *; }
# 2. Keep the entry class from obfuscation/renaming
-keep class com.qimian233.ztool.hook.HookInit { *; }
# 3. If HookManager uses reflection to find its own methods, keep it too
-keep class com.qimian233.ztool.hook.base.HookManager { *; }
# 4. Keep the HiddenApiBypass library
-keep class org.lsposed.hiddenapibypass.** { *; }
# 5. Generic reflection protection
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
# 6. libxposed service is a cross-process binder ABI. Keep the provider,
# AIDL stubs/proxies, service wrapper, helper, listener names, and parcelables
# stable so LSPosed can deliver and the app can consume the activation binder.
-keep class io.github.libxposed.service.** { *; }
-keep class io.github.libxposed.service.interfaces.** { *; }

# 7. Keep the app-side activation bridge intact. Release R8 can otherwise merge
# the listener into helper call sites and strip bridge methods that are only used
# by the libxposed callback path.
-keep class com.qimian233.ztool.ModuleActivationProbe { *; }
-keep class com.qimian233.ztool.ModuleActivationProbe$* { *; }
-keep class com.qimian233.ztool.XposedServiceBridge { *; }
-keep class com.qimian233.ztool.ZToolApplication { *; }
-keepclassmembers class * implements io.github.libxposed.service.XposedServiceHelper$OnServiceListener { *; }

# Gson TypeToken rules to preserve generic signatures
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
