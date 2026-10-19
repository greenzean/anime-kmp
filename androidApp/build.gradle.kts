plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.greenzean.animekmp"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "com.greenzean.animekmp"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode = 1
        versionName = "0.1.0"

        // Set animekmp.serverUrl in ~/.gradle/gradle.properties or with -P; no real URL is committed.
        val serverUrl =
            providers
                .gradleProperty("animekmp.serverUrl")
                .getOrElse("https://api.example.invalid/graphql")
        buildConfigField("String", "GRAPHQL_URL", "\"$serverUrl\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(projects.shared)
    implementation(libs.koin.android)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.activity.compose)
}
