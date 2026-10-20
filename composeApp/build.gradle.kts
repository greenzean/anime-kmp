plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    android {
        namespace = "com.greenzean.animekmp.composeapp"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
    }

    if (providers.gradleProperty("animekmp.enableIos").get().toBoolean()) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.lifecycle.runtime.compose)
        }
    }
}

dependencies {
    // Android Studio preview renderer (ComposeViewAdapter); kept off the release runtime classpath.
    androidRuntimeClasspath(compose.uiTooling)
}
