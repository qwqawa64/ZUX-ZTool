import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import java.text.SimpleDateFormat
import java.util.Date

fun getGitCommitCount(): Int {
    return try {
        val process = ProcessBuilder("git", "rev-list", "--count", "HEAD").start()
        process.waitFor()
        process.inputStream.bufferedReader().readText().trim().toInt()
    } catch (_: Exception) {
        println("Unable to get Git version count, fallback to 1")
        1
    }
}

fun getGitCommitHash(): String {
    return try {
        val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD").start()
        process.waitFor()
        process.inputStream.bufferedReader().readText().trim()
    } catch (_: Exception) {
        println("Unable to get Git commit hash, fallback to unknown")
        "unknown"
    }
}

fun getBuildTime(): String {
    return SimpleDateFormat("yyMMdd").format(Date())
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val releaseSigningEnvironment = mapOf(
    "ZTOOL_RELEASE_STORE_FILE" to System.getenv("ZTOOL_RELEASE_STORE_FILE"),
    "ZTOOL_RELEASE_STORE_PASSWORD" to System.getenv("ZTOOL_RELEASE_STORE_PASSWORD"),
    "ZTOOL_RELEASE_KEY_ALIAS" to System.getenv("ZTOOL_RELEASE_KEY_ALIAS"),
    "ZTOOL_RELEASE_KEY_PASSWORD" to System.getenv("ZTOOL_RELEASE_KEY_PASSWORD")
)
val releaseSigningEnabled = releaseSigningEnvironment.values.all { !it.isNullOrBlank() }
val partialReleaseSigningEnvironment = releaseSigningEnvironment
    .filterValues { !it.isNullOrBlank() }
    .keys

if (!releaseSigningEnabled && partialReleaseSigningEnvironment.isNotEmpty()) {
    error(
        "Incomplete release signing environment. Required variables: " +
            releaseSigningEnvironment.keys.joinToString()
    )
}

android {
    namespace = "com.qimian233.ztool"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.qimian233.ztool"
        minSdk = 27
        targetSdk = 37

        versionCode = getGitCommitCount()
        versionName = "Beta/${getBuildTime()}"
        buildConfigField("int", "GIT_COMMIT_COUNT", "${getGitCommitCount()}")
        buildConfigField("String", "GIT_COMMIT_HASH", "\"${getGitCommitHash()}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningEnabled) {
            create("release") {
                storeFile = file(releaseSigningEnvironment.getValue("ZTOOL_RELEASE_STORE_FILE")!!)
                storePassword = releaseSigningEnvironment.getValue("ZTOOL_RELEASE_STORE_PASSWORD")
                keyAlias = releaseSigningEnvironment.getValue("ZTOOL_RELEASE_KEY_ALIAS")
                keyPassword = releaseSigningEnvironment.getValue("ZTOOL_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseSigningEnabled) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

val releaseOutputDir = layout.buildDirectory.dir("outputs/apk/release")
val releaseDistributionDir = layout.projectDirectory.dir("release")
val releaseApkName = provider {
    val safeVersionName = android.defaultConfig.versionName.orEmpty().replace("/", "_")
    val versionCode = android.defaultConfig.versionCode
    "ZTool_${safeVersionName}_c${versionCode}.apk"
}
val releaseBaselineProfileName = releaseApkName.map { it.removeSuffix(".apk") + ".dm" }

val cleanReleaseDistribution by tasks.registering(Delete::class) {
    mustRunAfter("assembleRelease")
    delete(releaseDistributionDir)
}

tasks.register<Copy>("copyReleaseDistribution") {
    dependsOn("assembleRelease")
    dependsOn(cleanReleaseDistribution)
    from(releaseOutputDir) {
        include("*.apk")
        exclude("renamed/**")
        rename(".*\\.apk", releaseApkName.get())
    }
    from(releaseOutputDir) {
        include("output-metadata.json")
        filter { line: String ->
            line
                .replace("app-release-unsigned.apk", releaseApkName.get())
                .replace("app-release-unsigned.dm", releaseBaselineProfileName.get())
        }
    }
    from(releaseOutputDir.map { it.dir("baselineProfiles") }) {
        into("baselineProfiles")
        rename(".*\\.dm", releaseBaselineProfileName.get())
    }
    into(releaseDistributionDir)
}

tasks.register("copyRenamedReleaseApk") {
    dependsOn("copyReleaseDistribution")
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
    implementation(libs.androidx.navigationevent.compose)
    implementation(libs.miuix.android)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.savedstate)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    compileOnly(libs.xposed.api)
    implementation(libs.commonmark)
    implementation(libs.lunar)
    implementation(libs.hiddenapibypass)
    implementation(libs.gson)
}
