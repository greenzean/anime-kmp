plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
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

        withHostTest { isIncludeAndroidResources = true }
    }

    if (providers.gradleProperty("animekmp.enableIos").get().toBoolean()) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.apollo.runtime)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.xmlutil.core)
            implementation(libs.okio)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            api(libs.kotlinx.coroutines.core)
            api(libs.koin.core)
            api(libs.koin.core.viewmodel)
            api(libs.lifecycle.viewmodel)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
            implementation(libs.koin.android)
            implementation(libs.ktor.client.okhttp)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.ktor.client.mock)
            implementation(libs.koin.test)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.konsist)
            implementation(libs.junit)
            implementation(libs.robolectric)
        }
    }
}

apollo {
    service("anime") {
        packageName.set("com.greenzean.animekmp.shared.graphql")
        srcDir("src/commonMain/graphql")
        schemaFiles.from("src/commonMain/graphql/schema.graphqls")
        mapScalar("Instant", "kotlin.time.Instant", "com.greenzean.animekmp.shared.graphql.InstantAdapter")
        mapScalar("UUID", "kotlin.String")
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

tasks.withType<Test>().configureEach {
    jvmArgs(
        "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
    )
}
