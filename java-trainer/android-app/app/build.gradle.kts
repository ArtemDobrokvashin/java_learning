plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.javada.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.javada.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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

    // Автоматическая упаковка актуальных JSON-файлов курса (22 модуля, 99 уроков, 825 задач, 5 лаб)
    // напрямую из единого источника репозитория (../frontend/data и ../private) в assets APK без дублирования в Git
    val generatedCourseAssetsDir = layout.buildDirectory.dir("generated/courseAssets")

    val syncCourseAssets by tasks.registering(Copy::class) {
        from(rootProject.projectDir.resolve("../frontend/data")) {
            include("modules.json", "module-*.json", "labs.json")
            into("data")
        }
        from(rootProject.projectDir.resolve("../private")) {
            include("lab_cases.json")
            into("private")
        }
        into(generatedCourseAssetsDir)
    }

    sourceSets {
        getByName("main") {
            assets.srcDir(generatedCourseAssetsDir)
        }
    }

    tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
        dependsOn(syncCourseAssets)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Сериализация progress.json и данных курса (совместимость с веб-тренажёром и плагином IDEA)
    implementation("com.google.code.gson:gson:2.11.0")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
