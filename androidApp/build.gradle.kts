import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val releaseLocalProperties =
    Properties().apply {
        rootProject
            .file("local.properties")
            .takeIf { it.exists() }
            ?.reader()
            ?.use(::load)
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
        versionCode = 2
        versionName = "0.1.0"

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

        val googleServerClientId =
            providers
                .gradleProperty("animekmp.googleServerClientId")
                .orElse(localProperties.getProperty("animekmp.googleServerClientId") ?: "")
                .get()
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"$googleServerClientId\"")

        val malClientId =
            providers
                .gradleProperty("animekmp.malClientId")
                .orElse(localProperties.getProperty("animekmp.malClientId") ?: "")
                .get()
        buildConfigField("String", "MAL_CLIENT_ID", "\"$malClientId\"")

        val malRedirectUri =
            providers
                .gradleProperty("animekmp.malRedirectUri")
                .orElse(localProperties.getProperty("animekmp.malRedirectUri") ?: "")
                .get()
        val malRedirect = malRedirectUri.takeIf { it.isNotBlank() }?.let(::URI)
        if (malRedirect != null) {
            check(
                malRedirect.scheme == "https" &&
                    !malRedirect.host.isNullOrBlank() &&
                    malRedirect.rawQuery == null &&
                    malRedirect.rawFragment == null &&
                    malRedirect.rawUserInfo == null &&
                    malRedirect.port == -1 &&
                    malRedirect.host == malRedirect.host.lowercase(),
            ) {
                "animekmp.malRedirectUri must be an https URL with a lower-case host, no port, query, fragment or user info"
            }
        }
        val malRedirectPath = malRedirect?.path?.ifEmpty { "/" } ?: "/"
        val malRedirectPathAlt =
            when {
                malRedirectPath == "/" -> "/"
                malRedirectPath.endsWith("/") -> malRedirectPath.dropLast(1)
                else -> "$malRedirectPath/"
            }
        buildConfigField("String", "MAL_REDIRECT_URI", "\"$malRedirectUri\"")
        manifestPlaceholders["malRedirectEnabled"] = (malRedirect != null).toString()
        manifestPlaceholders["malRedirectHost"] = malRedirect?.host ?: "mal-redirect.invalid"
        manifestPlaceholders["malRedirectPath"] = malRedirectPath
        manifestPlaceholders["malRedirectPathAlt"] = malRedirectPathAlt
    }

    buildFeatures {
        buildConfig = true
    }

    val keystorePath = providers.environmentVariable("ANIMEKMP_KEYSTORE_PATH").orNull
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = providers.environmentVariable("ANIMEKMP_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("ANIMEKMP_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("ANIMEKMP_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
            val releaseServerUrl =
                providers
                    .gradleProperty("animekmp.serverUrl.release")
                    .orElse(releaseLocalProperties.getProperty("animekmp.serverUrl.release") ?: "")
                    .get()
            buildConfigField(
                "String",
                "GRAPHQL_URL",
                "\"${releaseServerUrl.ifBlank { "https://api.example.invalid/graphql" }}\"",
            )
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
    implementation(libs.androidx.browser)
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    val releaseServerUrl =
        providers
            .gradleProperty("animekmp.serverUrl.release")
            .orElse(releaseLocalProperties.getProperty("animekmp.serverUrl.release") ?: "")
    doFirst {
        val releaseUri = runCatching { URI(releaseServerUrl.get()) }.getOrNull()
        check(releaseUri?.scheme == "https" && !releaseUri.host.isNullOrBlank()) {
            "animekmp.serverUrl.release must be an https URL for release builds"
        }
        listOf("animekmp.googleServerClientId", "animekmp.malClientId", "animekmp.malRedirectUri").forEach { key ->
            val value =
                providers
                    .gradleProperty(key)
                    .orElse(releaseLocalProperties.getProperty(key) ?: "")
                    .get()
            check(value.isNotBlank()) { "$key must be set for release builds" }
        }
    }
}
