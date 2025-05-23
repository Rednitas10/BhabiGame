// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "7.4.2" apply false // Use a recent stable version
    id("com.android.library") version "7.4.2" apply false // Use a recent stable version
    kotlin("android") version "1.8.0" apply false // Use a recent stable version (match compose compiler if possible)
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
