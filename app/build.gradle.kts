plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "com.lgram.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lgram.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.1.1" // синхронізовано з version.properties, не редагувати вручну

        ndk {
            // armeabi-v7a + arm64-v8a покривають майже всі реальні Android-пристрої.
            // NDK саму ставити не треба — нативні бібліотеки Chaquopy вже готові.
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    signingConfigs {
        getByName("debug") {
            // Фіксований debug-keystore (закомічений у keystore/debug.keystore), щоб
            // кожна збірка з GitHub Actions мала ОДНАКОВИЙ підпис — інакше раннер щоразу
            // генерує новий debug.keystore, Android бачить нову збірку як "інший застосунок"
            // і вимагає видалити попередню перед встановленням (а разом з нею — дозвіл на сховище).
            storeFile = file("../keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

chaquopy {
    defaultConfig {
        pip {
            // pyaes (залежність Telethon) не має готового .whl на PyPI, лише вихідний
            // код, а pip у Chaquopy 17+ не збирає пакети з вихідників — тому тут
            // підсовуємо власноруч зібраний локальний wheel (чистий Python, без
            // платформозалежного коду, тому збирається без проблем).
            install("wheels/pyaes-1.6.1-py3-none-any.whl")
            install("telethon")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("com.google.zxing:core:3.5.3") // генерація QR-коду для входу
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
