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
}

android {
    namespace = "com.qimian233.ztool"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.qimian233.ztool"
        minSdk = 27
        targetSdk = 36

        versionCode = getGitCommitCount()
        versionName = "Demo/${getBuildTime()}"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.swiperefreshlayout)
    implementation(files("libs/swiperefreshlayout-1.1.0.pom"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    compileOnly("de.robv.android.xposed:api:82")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("cn.6tail:lunar:1.7.5")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")
    implementation("com.google.code.gson:gson:2.10.1")
}
