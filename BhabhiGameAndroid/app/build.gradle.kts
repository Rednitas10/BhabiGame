plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    compileSdk = 33 // Or latest stable
    defaultConfig {
        applicationId = "com.example.bhabhigameandroid"
        minSdk = 21
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true // Enable Jetpack Compose
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.3.2" // Use a recent stable version
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.9.0") // Or latest stable
    implementation("androidx.activity:activity-compose:1.6.1") // Or latest stable
    implementation("androidx.compose.ui:ui:1.3.3") // Or latest stable
    implementation("androidx.compose.material:material:1.3.1") // Or latest stable
    implementation("androidx.compose.ui:ui-tooling-preview:1.3.3") // Or latest stable
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2") // Added for viewModelScope if GameEngine uses it

    // Test dependencies
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.1") // For testing coroutines and StateFlow
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.3.3")
    debugImplementation("androidx.compose.ui:ui-tooling:1.3.3")
}
