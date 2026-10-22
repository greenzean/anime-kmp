package com.greenzean.animekmp.server

enum class AppEnv { DEV, PROD }

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
)

/** `googleClientId` is the web/server OAuth client ID that Google ID tokens must be issued for (`aud`). */
data class AuthConfig(
    val googleClientId: String,
    val jwtSecret: String,
)

private const val MIN_JWT_SECRET_BYTES = 32

data class AppConfig(
    val host: String,
    val port: Int,
    val env: AppEnv,
    val database: DatabaseConfig? = null,
    val auth: AuthConfig? = null,
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
            val auth = authFromEnv(env, appEnv)
            return AppConfig(
                host = env["HOST"] ?: "0.0.0.0",
                port = port,
                env = appEnv,
                database = database,
                auth = auth,
            )
        }

        // Both or neither. Blank counts as unset (compose passes empty strings through). Prod requires both.
        private fun authFromEnv(
            env: Map<String, String>,
            appEnv: AppEnv,
        ): AuthConfig? {
            val clientId = env["GOOGLE_CLIENT_ID"]?.trim().orEmpty()
            val secret = env["JWT_SECRET"].orEmpty()
            if (clientId.isEmpty() && secret.isEmpty()) {
                check(appEnv != AppEnv.PROD) { "GOOGLE_CLIENT_ID and JWT_SECRET are required when APP_ENV=prod" }
                return null
            }
            check(clientId.isNotEmpty()) { "GOOGLE_CLIENT_ID is required when JWT_SECRET is set" }
            check(secret.isNotEmpty()) { "JWT_SECRET is required when GOOGLE_CLIENT_ID is set" }
            check(secret.toByteArray().size >= MIN_JWT_SECRET_BYTES) {
                "JWT_SECRET must be at least $MIN_JWT_SECRET_BYTES bytes"
            }
            return AuthConfig(clientId, secret)
        }
    }
}
