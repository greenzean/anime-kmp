plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.apollo)
}

kotlin {
    android {
        namespace = "com.greenzean.animekmp.shared"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()

        withHostTest {}
    }

    if (providers.gradleProperty("animekmp.enableIos").get().toBoolean()) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.apollo.runtime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

apollo {
    service("anime") {
        packageName.set("com.greenzean.animekmp.shared.graphql")
        srcDir("src/commonMain/graphql")
        schemaFiles.from("src/commonMain/graphql/schema.graphqls")
    }
}
