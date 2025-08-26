import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.aboutlibraries)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget("1.8")
    }
}

android {
    namespace = "cn.qinxiandiqi.photochecker"
    compileSdk = 36

    defaultConfig {
        applicationId = "cn.qinxiandiqi.photochecker"
        minSdk = 24
        targetSdk = 36
        versionCode = 4
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        val keyPropertiesFile = file("key/key.properties")
        val keyProperties = Properties()
        keyProperties.load(keyPropertiesFile.inputStream())

        fun createOrUpdateSigningConfig(name: String) {
            maybeCreate(name).apply {
                storeFile = file(keyProperties.getProperty("${name}.store"))
                storePassword = keyProperties.getProperty("${name}.storePassword")
                keyAlias = keyProperties.getProperty("${name}.keyAlias")
                keyPassword = keyProperties.getProperty("${name}.keyPassword")
            }
        }

        createOrUpdateSigningConfig("debug")
        createOrUpdateSigningConfig("release")
        createOrUpdateSigningConfig("upload")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("upload")
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    productFlavors {
        create("default") {
            dimension = "default"
        }
        create("upload") {
            dimension = "default"
            signingConfig = signingConfigs.getByName("upload")
        }
    }
    flavorDimensions("default")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material3.window.size)
    implementation(libs.androidx.exifinterface)
    implementation(libs.coil)
    implementation(libs.coil.compose)
    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose.m3)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}