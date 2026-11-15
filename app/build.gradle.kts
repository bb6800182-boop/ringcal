import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    // 程式碼的套件（namespace）維持 com.c520.likexiang；對外的套件名稱（applicationId）2026-11-15 隨英文名改成 com.c520.ringcal
    namespace = "com.c520.likexiang"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.c520.ringcal"
        // Android 13 起才有 USE_EXACT_ALARM 與通知權限，從 33 起跳可省掉舊版相容分支
        minSdk = 33
        targetSdk = 36
        versionCode = 13
        versionName = "1.4.1"
    }

    // 簽章放在專案資料夾旁邊的 calendar-alarm-signing（不進版控；用 Dropbox 同步，每台電腦都用同一份）：
    // 正式版的金鑰與密碼寫在 signing.properties；開發版用那裡的 debug.keystore，換一台電腦編的開發版也能直接更新手機上的 App。
    // 沒有這個資料夾的電腦照樣能編 debug 版（用 Android 預設的 debug 金鑰），只是編不出簽好章的正式版。
    val signingDir = rootDir.resolveSibling("calendar-alarm-signing")
    val signing = Properties().apply { signingDir.resolve("signing.properties").takeIf { it.isFile }?.reader()?.use { load(it) } }
    signingConfigs {
        signingDir.resolve("debug.keystore").takeIf { it.isFile }?.let { getByName("debug").storeFile = it }
        signing.getProperty("storeFile")?.let { store ->
            create("release") {
                storeFile = signingDir.resolve(store)
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // 資安相關的 lint 一律當錯誤
        error += listOf(
            "ExportedReceiver", "ExportedService", "UnsafeImplicitIntentLaunch",
            "MutableImplicitPendingIntent", "UnspecifiedRegisterReceiverFlag",
        )
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.appwidget)

    testImplementation(libs.junit)
}

// 單元測試固定用中文（台灣）跑：畫面文字跟著手機語言（L），測試預期的是中文；英文的測試自己切換語言
tasks.withType<Test>().configureEach {
    systemProperty("user.language", "zh")
    systemProperty("user.country", "TW")
}
