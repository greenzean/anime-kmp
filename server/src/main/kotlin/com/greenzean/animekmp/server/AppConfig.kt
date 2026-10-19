package com.greenzean.animekmp.server

enum class AppEnv { DEV, PROD }

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
)

data class AppConfig(
    val host: String,
    val port: Int,
    val env: AppEnv,
    val database: DatabaseConfig? = null,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): AppConfig {
            val port =
                env["PORT"]?.let {
                    it.toIntOrNull()?.takeIf { p -> p in 1..65535 }
                        ?: error("PORT must be an integer between 1 and 65535, got '$it'")
                } ?: 8080
            val appEnv =
                env["APP_ENV"]?.let {
                    AppEnv.entries.firstOrNull { e -> e.name.equals(it, ignoreCase = true) }
                        ?: error("APP_ENV must be 'dev' or 'prod', got '$it'")
                } ?: AppEnv.DEV
            val database =
                env["DATABASE_URL"]?.let {
                    DatabaseConfig(
                        url = it,
                        user = env["DATABASE_USER"] ?: error("DATABASE_USER is required when DATABASE_URL is set"),
                        password =
                            env["DATABASE_PASSWORD"] ?: error("DATABASE_PASSWORD is required when DATABASE_URL is set"),
                    )
                }
            return AppConfig(host = env["HOST"] ?: "0.0.0.0", port = port, env = appEnv, database = database)
        }
    }
}
