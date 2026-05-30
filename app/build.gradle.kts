import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.text.SimpleDateFormat
import java.util.Date

fun getGitCommitCount(): Int {
    return try {
        val process = ProcessBuilder("git", "rev-list", "--count", "HEAD").start()
        process.waitFor()
        process.inputStream.bufferedReader().readText().trim().toInt()
    } catch (e: Exception) {
        println("无法获取 Git 版本号，降级为 1")
        1
    }
}


fun getBuildTime(): String {
    return SimpleDateFormat("yyMMdd").format(Date())
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.qimian233.ztool"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.qimian233.ztool"
        minSdk = 27
        targetSdk = 36

        versionCode = getGitCommitCount()
        versionName = "Beta/${getBuildTime()}"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    applicationVariants.all {
        val variant = this
        variant.outputs
            .map { it as BaseVariantOutputImpl }
            .forEach { output ->
                if (variant.buildType.name == "release") {
                    val appName = "ZTool"

                    // 2. 获取版本信息
                    // 注意：你的 versionName 含有 "/"，必须替换掉，否则会报错
                    val safeVersionName = variant.versionName.replace("/", "_")
                    val vCode = variant.versionCode

                    // 3. 拼接新文件名
                    val newFileName = "${appName}_${safeVersionName}_c${vCode}.apk"

                    // 4. 应用新文件名
                    output.outputFileName = newFileName
                }
            }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.miuix.android)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.savedstate)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    compileOnly("de.robv.android.xposed:api:82")
    implementation("cn.6tail:lunar:1.7.5")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")
    implementation("com.google.code.gson:gson:2.10.1")
}
