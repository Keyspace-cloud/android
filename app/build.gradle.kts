plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
}

android {
    namespace = AppBuildConfig.namespace
    compileSdk = AppBuildConfig.compileSdk

    defaultConfig {
        applicationId = AppBuildConfig.appId
        minSdk = AppBuildConfig.minSdk
        targetSdk = AppBuildConfig.targetSdk
        versionCode = AppBuildConfig.versionCode
        versionName = AppBuildConfig.versionName
        multiDexEnabled = true

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
        sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    }

    kotlinOptions {
        jvmTarget = libs.versions.java.get()
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("x86", "x86_64", "armeabi-v7a", "armeabi-v8a")
            isUniversalApk = true
        }
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.android.multidex)
    implementation(libs.android.volley) // Library to make POST and GET requests

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.autofill)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.drawerlayout)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.gridlayout)
    implementation(libs.androidx.legacy.support.v4)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.security.app.authenticator) // for biometrics + keystore
    implementation(libs.androidx.security.crypto)

    implementation(libs.google.material)
    implementation(libs.google.gson) // JSON parsing
    implementation(libs.google.zxing)

    implementation(libs.kotlin.bip39) // bip39 mnemonics
    implementation(libs.code.scanner) // QR Code scanner
    implementation(libs.jackson.module.kotlin)
    implementation(libs.colorpicker)
    implementation(libs.awesomeqrcode)
    implementation(libs.swipeable.recyclerview) // For swipeable recyclerview
    implementation(libs.lazysodium.android) // for Argon2i and ed25519 keypair
    implementation(libs.permissionx) // For permissions
    implementation(libs.nv.websocket.client)
    implementation(libs.zxcvbn) // password strength
    implementation(libs.library)
    implementation(libs.rootbeer.lib) // To check if device is rooted
    implementation(libs.markdown.processor)
    implementation(libs.rxmarkdown.wrapper)
    implementation(libs.kotlin.onetimepassword) // 2FA Tokens
    implementation(libs.markdownedittext) // For Notes markdown
    implementation(libs.rxandroid)
    implementation(libs.rxjava)
    implementation(libs.jna) // LibSodium JNA libraries

    // ACRA for crash logging
    implementation(libs.acra.mail) // mail component
    implementation(libs.acra.dialog) // dialog component
    implementation(libs.anggrayudi.storage) // access storage

    // Testing
    implementation(libs.androidx.test.core.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.security.app.authenticator) // For App Authentication API testing
}
