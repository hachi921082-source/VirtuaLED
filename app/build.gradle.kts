plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // パッケージ名 com.virtualed.app は仮のもの。最終的なドメイン決定待ち
    // (docs/spec.md §"Package/Build Identifier" 参照)。
    namespace = "com.virtualed.app"
    // compileSdk/targetSdk は spec の対象範囲 API 34-35 のうち 35 に統一。確定事項ではない
    // (docs/spec.md §2 参照)。
    compileSdk = 35

    defaultConfig {
        applicationId = "com.virtualed.app"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    // docs/plans/other/dnd-handling/PLAN.md §2 — hide_during_dnd 設定の永続化に使用
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    // docs/plans/other/dnd-handling/PLAN.md §5 — Service 側の起動時キャッシュ読み込み用コルーチンスコープに使用
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
