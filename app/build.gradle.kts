@file:Suppress(
    "GradleDependency",
    "DifferentKotlinGradleVersion",
    "ExpiredTargetSdkVersion",
    "OldTargetApi",
    "AndroidSdkAttribute",
    "DuplicatePlatformDependency",
)

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.hoshizora.notifier"
    // Android 15 (API 35) 正式リリース仕様で固定 (最新API 36/37等はAGP 8.5.2互換性考慮のため見送り)
    //noinspection OldTargetApi, GradleDependency
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hoshizora.notifier"
        minSdk = 26
        // AGP 8.5.2 および Google Play の最新安定ポリシーに適合した Target SDK
        //noinspection ExpiredTargetSdkVersion, OldTargetApi, GradleDependency
        targetSdk = 35
        versionCode = 7
        versionName = "1.1.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Compose BOM (ビルドプラットフォーム定義)
    //noinspection GradleDependency, DuplicatePlatformDependency
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    //noinspection GradleDependency, DuplicatePlatformDependency
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.01"))

    // AGP 8.5.2 との互換性およびアプリ動作安定性を考慮し、最適な安定バージョンで固定
    //noinspection GradleDependency
    implementation("androidx.core:core-ktx:1.15.0")
    //noinspection GradleDependency
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    //noinspection GradleDependency
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    //noinspection GradleDependency
    implementation("androidx.navigation:navigation-compose:2.10.2")

    // Room (ローカルDB: AGP 8.5.2 互換バージョン 2.6.1 で固定)
    //noinspection GradleDependency
    implementation("androidx.room:room-runtime:2.6.1")
    //noinspection GradleDependency
    implementation("androidx.room:room-ktx:2.6.1")
    //noinspection GradleDependency
    ksp("androidx.room:room-compiler:2.6.1")

    // WorkManager (AGP 8.5.2 互換バージョン 2.10.0 で固定)
    //noinspection GradleDependency
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Location (現在地取得: 21.3.0 で固定)
    //noinspection GradleDependency
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Retrofit + Moshi (2.x 系最新安定バージョン 2.11.0 で固定。3.0.0 はメジャーアップデートのため維持)
    //noinspection GradleDependency, NewerVersionAvailable
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    //noinspection GradleDependency, NewerVersionAvailable
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    //noinspection GradleDependency, NewerVersionAvailable
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.2")

    // Test dependencies
    //noinspection GradleDependency
    testImplementation("junit:junit:4.13.2")
    //noinspection GradleDependency
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    //noinspection GradleDependency
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // Debug dependencies
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
