plugins {
    id("com.android.application") version "8.5.0"
}

repositories {
    mavenCentral()
    google()
}

android {
    namespace = "com.jadx.dexeditor"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jadx.dexeditor"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module",
                "META-INF/versions/**",
                "META-INF/maven/**",
                "META-INF/native-image/**",
                "META-INF/proguard/**"
            )
        }
    }
}

configurations.all {
    exclude(group = "com.google.future", module = "listenablefuture")
    exclude(group = "com.google.guava", module = "listenablefuture")
}

dependencies {
    implementation("io.github.skylot:jadx-core:1.5.6")
    implementation("io.github.skylot:jadx-dex-input:1.5.6")
    implementation("io.github.skylot:jadx-java-convert:1.5.6")
    implementation("io.github.skylot:jadx-smali-input:1.5.6")
    implementation("com.android.tools.smali:smali:3.0.9") {
        exclude(group = "com.beust", module = "jcommander")
    }
    implementation("com.android.tools.smali:smali-baksmali:3.0.9") {
        exclude(group = "com.beust", module = "jcommander")
    }

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.fragment:fragment-ktx:1.7.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
