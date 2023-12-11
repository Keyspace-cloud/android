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

    packaging {
        resources {
            excludes.addAll(
                listOf(
                    "META-INF/DEPENDENCIES",
                    "META-INF/LICENSE",
                    "META-INF/LICENSE.txt",
                    "META-INF/license.txt",
                    "META-INF/NOTICE",
                    "META-INF/NOTICE.txt",
                    "META-INF/notice.txt",
                    "META-INF/ASL2.0",
                    "META-INF/AL2.0",
                    "META-INF/LGPL2.1",
                    "META-INF/*.kotlin_module"
                )
            )
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

    implementation(libs.google.gson) // JSON parsing
    implementation(libs.google.material)
    implementation(libs.google.zxing)

    implementation(libs.awesomeqrcode)
    implementation(libs.code.scanner) // QR Code scanner
    implementation(libs.colorpicker)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.bip39) // bip39 mnemonics
    implementation(libs.kotlin.onetimepassword) // 2FA Tokens
    implementation(libs.markdown.processor)
    implementation(libs.markdownedittext) // For Notes markdown
    implementation(libs.nv.websocket.client)
    implementation(libs.permissionx) // For permissions
    implementation(libs.rootbeer.lib) // To check if device is rooted
    implementation(libs.rxandroid)
    implementation(libs.rxjava)
    implementation(libs.rxmarkdown.wrapper)
    implementation(libs.swipeable.recyclerview) // For swipeable recyclerview
    implementation(libs.zxcvbn) // password strength

    //region TODO: Using artifact.type = "aar" does not seem to work.
    //        The version catalog will still use .jar, which will result
    //        in duplicate classes.
    //noinspection UseTomlInstead
    implementation("net.java.dev.jna:jna:5.10.0@aar")
//    implementation(libs.jna) { // LibSodium JNA libraries
//        artifact {
//            type = "aar"
//        }
//    }

    //noinspection UseTomlInstead
    implementation("com.pixplicity.sharp:library:1.1.2@aar")
//    implementation(libs.pixplicity.library) {
//        artifact {
//            type = "aar"
//        }
//    }

    //noinspection UseTomlInstead
    implementation("com.goterl:lazysodium-android:5.0.2@aar")
//    implementation(libs.lazysodium.android) { // for Argon2i and ed25519 keypair
//        artifact {
//            type = "aar"
//        }
//    }
    //endregion

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
