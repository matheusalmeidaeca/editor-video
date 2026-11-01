plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Assinatura fixa: só ativa quando o workflow (ou você, localmente) define estas variáveis.
val keystoreFile: String? = System.getenv("KEYSTORE_FILE")
val temKeystore = keystoreFile != null && File(keystoreFile!!).exists()

android {
    namespace = "com.editorvideo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.editorvideo"
        minSdk = 29
        targetSdk = 34
        versionCode = 16
        versionName = "3.2.10"
    }

    signingConfigs {
        if (temKeystore) {
            create("fixa") {
                storeFile = file(keystoreFile!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("debug") {
            signingConfigs.findByName("fixa")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.media3:media3-common:1.4.1")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-transformer:1.4.1")
    implementation("androidx.media3:media3-effect:1.4.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
