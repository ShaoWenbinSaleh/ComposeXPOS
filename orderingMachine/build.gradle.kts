import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinSerialization)
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
            baseName = "OrderingMachineApp"
            isStatic = true
            binaryOption("bundleId", "com.cofopt.composexpos.orderingmachine.framework")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.components.resources)
            implementation(compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.uiToolingPreview)
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
        }
        androidMain.dependencies {
            implementation(project(":shared"))

            implementation("androidx.compose.runtime:runtime")
            implementation("androidx.compose.foundation:foundation:1.10.0")
            implementation("androidx.compose.foundation:foundation-layout:1.10.0")
            implementation("androidx.compose.material3:material3:1.4.0")
            implementation("androidx.compose.material3:material3-window-size-class:1.4.0")
            implementation("androidx.compose.material3:material3-adaptive-navigation-suite:1.4.0")
            implementation("androidx.compose.material:material-icons-extended")
            implementation("androidx.compose.ui:ui:1.9.5")
            implementation("androidx.compose.ui:ui-util")
            implementation("androidx.compose.ui:ui-tooling-preview")
            implementation("androidx.compose.animation:animation")

            implementation("androidx.activity:activity-compose:1.8.2")
            implementation("androidx.core:core-ktx:1.12.0")
            implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
            implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
            implementation("androidx.navigation:navigation-compose:2.7.7")
            implementation("androidx.constraintlayout:constraintlayout-compose:1.1.1")
            implementation("androidx.constraintlayout:constraintlayout:2.2.1")
            implementation("androidx.core:core-splashscreen:1.0.1")

            implementation("androidx.datastore:datastore-core-android:1.2.0")
            implementation("androidx.datastore:datastore-preferences:1.2.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("org.nanohttpd:nanohttpd:2.3.1")

            implementation("com.android.volley:volley:1.2.1")
            implementation("com.google.accompanist:accompanist-systemuicontroller:0.36.0")
            implementation("com.google.accompanist:accompanist-flowlayout:0.36.0")
            implementation("com.google.accompanist:accompanist-webview:0.36.0")

            implementation("io.coil-kt:coil-compose:2.6.0")
            implementation("io.coil-kt:coil-video:2.6.0")
        }
    }
}

android {
    namespace = "com.cofopt.orderingmachine"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cofopt.orderingmachine"
        minSdk = 25
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    add("androidMainImplementation", platform("androidx.compose:compose-bom:2024.04.00"))

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.compose.ui:ui-test")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
