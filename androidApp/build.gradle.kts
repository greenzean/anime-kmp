import java.util.Properties

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

        // Set animekmp.serverUrl with -P, in ~/.gradle/gradle.properties or in the gitignored
        // local.properties; no real URL is committed.
        val localProperties =
            Properties().apply {
                rootProject
                    .file("local.properties")
                    .takeIf { it.exists() }
                    ?.reader()
                    ?.use(::load)
            }
        val serverUrl =
            providers
                .gradleProperty("animekmp.serverUrl")
                .orElse(localProperties.getProperty("animekmp.serverUrl") ?: "https://api.example.invalid/graphql")
                .get()
        buildConfigField("String", "GRAPHQL_URL", "\"$serverUrl\"")

        // The *web* OAuth client ID, the same value as the server's GOOGLE_CLIENT_ID. Empty means sign-in
        // reports "not configured" and the rest of the app works.
        val googleServerClientId =
            providers
                .gradleProperty("animekmp.googleServerClientId")
                .orElse(localProperties.getProperty("animekmp.googleServerClientId") ?: "")
                .get()
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"$googleServerClientId\"")
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
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
}
