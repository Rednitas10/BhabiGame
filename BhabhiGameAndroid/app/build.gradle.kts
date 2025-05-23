plugins {
    id("com.android.application")
    kotlin("android")
    id("com.google.gms.google-services") version "4.4.1" apply false // Added Google Play services plugin
}

android {
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.bhabhigameandroid"
        minSdk = 21
        targetSdk = 34
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
        kotlinCompilerExtensionVersion = "1.5.3" 
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0") 
    implementation("androidx.activity:activity-compose:1.8.0") 
    implementation("androidx.compose.ui:ui:1.5.4") 
    implementation("androidx.compose.material:material:1.5.4") 
    implementation("androidx.compose.ui:ui-tooling-preview:1.5.4") 
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2") 
    implementation("androidx.navigation:navigation-compose:2.7.5") // Updated to a more recent stable version

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.8.0")) // Firebase BoM
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-functions-ktx") // For Firebase Cloud Functions
    implementation("com.google.firebase:firebase-database-ktx") // For Realtime Database (connection state)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3") // For Firebase tasks with coroutines


    // Test dependencies
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3") // Updated to a more recent stable version
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.5.4")
    debugImplementation("androidx.compose.ui:ui-tooling:1.5.4")
}

// Apply the Google services plugin at the bottom
apply(plugin = "com.google.gms.google-services")
