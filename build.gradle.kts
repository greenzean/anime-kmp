plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.detekt) apply false
}

allprojects {
    group = "com.greenzean.animekmp"
    version = "0.1.0"
}

val ktlintVersion = libs.versions.ktlint.get()
val composeRulesVersion = libs.versions.composeRules.get()
val composeRulesDetekt = libs.compose.rules.detekt

subprojects {
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "dev.detekt")

    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            targetExclude("**/build/**")
            ktlint(ktlintVersion)
                .customRuleSets(listOf("io.nlopez.compose.rules:ktlint:$composeRulesVersion"))
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(ktlintVersion)
        }
    }

    extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        source.setFrom("src")
    }

    dependencies {
        "detektPlugins"(composeRulesDetekt)
    }
}

// Opt-in: ./gradlew installGitHooks
tasks.register<Copy>("installGitHooks") {
    from("scripts/pre-commit")
    into(".git/hooks")
    filePermissions { unix("rwxr-xr-x") }
}
