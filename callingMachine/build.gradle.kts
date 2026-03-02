import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    js {
        browser()
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "CallingMachineApp"
            isStatic = true
            binaryOption("bundleId", "com.cofopt.composexpos.callingmachine.framework")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.uiToolingPreview)
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.9.3")
            implementation("androidx.compose.ui:ui")
            implementation("androidx.compose.ui:ui-tooling-preview")
            implementation("androidx.compose.foundation:foundation")
            implementation("androidx.compose.foundation:foundation-layout")
            implementation("androidx.compose.material3:material3")
            implementation("androidx.compose.runtime:runtime")
            implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
            implementation("androidx.leanback:leanback:1.0.0")
            implementation("androidx.leanback:leanback-preference:1.0.0")

            implementation("androidx.core:core-ktx:1.17.0")
            implementation("androidx.appcompat:appcompat:1.7.1")
            implementation("com.google.android.material:material:1.13.0")
            implementation("androidx.activity:activity:1.12.2")
            implementation("androidx.constraintlayout:constraintlayout:2.2.1")
            implementation("org.java-websocket:Java-WebSocket:1.5.6")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

        }
    }
}

android {
    namespace = "com.cofopt.callingmachine"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cofopt.callingmachine"
        minSdk = 27
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

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
    add("androidMainImplementation", platform("androidx.compose:compose-bom:2024.10.00"))

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
