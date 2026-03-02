import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
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
            baseName = "CashRegisterApp"
            isStatic = true
            binaryOption("bundleId", "com.cofopt.composexpos.cashregister.framework")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.components.resources)
            implementation(compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.uiToolingPreview)
        }
        androidMain.dependencies {
            implementation("androidx.compose.runtime:runtime")
            implementation("androidx.compose.foundation:foundation")
            implementation("androidx.compose.foundation:foundation-layout")
            implementation("androidx.compose.material3:material3:1.4.0")
            implementation("androidx.compose.material3:material3-window-size-class:1.4.0")
            implementation("androidx.compose.material3:material3-adaptive-navigation-suite:1.4.0")
            implementation("androidx.compose.material:material-icons-extended")
            implementation("androidx.compose.ui:ui:1.10.0")
            implementation("androidx.compose.ui:ui-util")
            implementation("androidx.compose.ui:ui-tooling-preview")

            implementation("androidx.activity:activity-compose:1.12.0")
            implementation("androidx.core:core-ktx:1.17.0")
            implementation("androidx.appcompat:appcompat:1.7.0")
            implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
            implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
            implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
            implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
            implementation("androidx.savedstate:savedstate:1.2.1")
            implementation("androidx.navigation:navigation-compose:2.9.6")
            implementation("androidx.constraintlayout:constraintlayout-compose:1.1.1")

            implementation("androidx.datastore:datastore-core-android:1.2.0")
            implementation("androidx.datastore:datastore-preferences:1.2.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

            implementation("com.google.accompanist:accompanist-systemuicontroller:0.36.0")
            implementation("com.google.accompanist:accompanist-flowlayout:0.36.0")
            implementation("com.google.accompanist:accompanist-webview:0.36.0")

            implementation("org.nanohttpd:nanohttpd:2.3.1")
            implementation("com.squareup.okhttp3:okhttp:5.3.2")

            implementation("androidx.room:room-runtime:2.8.4")
            implementation("androidx.room:room-ktx:2.8.4")

        }
    }
}

android {
    namespace = "com.cofopt.cashregister"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cofopt.cashregister"
        minSdk = 26
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
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        aidl = true
    }
}

dependencies {
    add("androidMainImplementation", platform("androidx.compose:compose-bom:2025.11.01"))
    add("kspAndroid", "androidx.room:room-compiler:2.8.4")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.compose.ui:ui-test")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
