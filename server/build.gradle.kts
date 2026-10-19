plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

application {
    mainClass = "com.greenzean.animekmp.server.ApplicationKt"
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.graphql.kotlin.ktor.server)
    implementation(libs.logback.classic)
    implementation(libs.logstash.logback.encoder)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.server.test.host)
}

// ADR 0004: SchemaExportTest compares the printed SDL with the committed file, or rewrites it with -PupdateSchema.
val updateSchema = providers.gradleProperty("updateSchema").isPresent

tasks.test {
    val schemaFile = rootProject.file("shared/src/commonMain/graphql/schema.graphqls")
    systemProperty("updateSchema", updateSchema.toString())
    systemProperty("schemaFile", schemaFile.absolutePath)
    // Editing the SDL by hand must rerun the drift check.
    inputs.file(schemaFile).optional().withPropertyName("schemaFile")
    if (updateSchema) outputs.upToDateWhen { false }
}
