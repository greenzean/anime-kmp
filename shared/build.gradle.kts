plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.apollo)
    alias(libs.plugins.sqldelight)
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
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            api(libs.kotlinx.coroutines.core)
            api(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
            implementation(libs.koin.android)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

apollo {
    service("anime") {
        packageName.set("com.greenzean.animekmp.shared.graphql")
        srcDir("src/commonMain/graphql")
        schemaFiles.from("src/commonMain/graphql/schema.graphqls")
        // Custom scalars (ADR 0006). Server hooks: server/.../graphql/SyncSchemaHooks.kt.
        mapScalar("Instant", "kotlin.time.Instant", "com.greenzean.animekmp.shared.graphql.InstantAdapter")
        mapScalar("UUID", "kotlin.String")
        mapScalar("Long", "kotlin.Long")
    }
}

sqldelight {
    databases {
        create("AnimeDatabase") {
            packageName.set("com.greenzean.animekmp.shared.core.data.db")
            verifyMigrations.set(true)
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
        }
    }
}
