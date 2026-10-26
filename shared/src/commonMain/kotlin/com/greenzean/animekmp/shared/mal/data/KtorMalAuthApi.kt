package com.greenzean.animekmp.shared.mal.data

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.mal.domain.MalAuthApi
import com.greenzean.animekmp.shared.mal.domain.MalAuthError
import com.greenzean.animekmp.shared.mal.domain.MalConfig
import com.greenzean.animekmp.shared.mal.domain.MalTokens
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private const val TOKEN_URL = "https://myanimelist.net/v1/oauth2/token"
private const val BAD_REQUEST = 400
private const val UNAUTHORIZED = 401
private const val FORBIDDEN = 403
private const val TOO_MANY_REQUESTS = 429
private const val REDIRECT_FROM = 300
private const val REDIRECT_UNTIL = 399
private const val SERVER_ERROR_FROM = 500

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
)

@Serializable
private data class ErrorResponse(
    val error: String? = null,
)

class KtorMalAuthApi(
    private val client: HttpClient,
    private val config: MalConfig,
) : MalAuthApi {
    override suspend fun exchangeCode(
        code: String,
        codeVerifier: String,
    ): Result<MalTokens, MalAuthError> =
        requestTokens(rejectionIsDeadSession = false) {
            parameters {
                append("client_id", config.clientId)
                append("grant_type", "authorization_code")
                append("code", code)
                append("code_verifier", codeVerifier)
                append("redirect_uri", config.redirectUri)
            }
        }

    override suspend fun refresh(refreshToken: String): Result<MalTokens, MalAuthError> =
        requestTokens(rejectionIsDeadSession = true) {
            parameters {
                append("client_id", config.clientId)
                append("grant_type", "refresh_token")
                append("refresh_token", refreshToken)
            }
        }

    private suspend fun requestTokens(
        rejectionIsDeadSession: Boolean,
        form: () -> Parameters,
    ): Result<MalTokens, MalAuthError> =
        try {
            toResult(client.submitForm(TOKEN_URL, form()), rejectionIsDeadSession)
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            Result.Error(MalAuthError.NO_INTERNET)
        } catch (_: SerializationException) {
            Result.Error(MalAuthError.UNKNOWN)
        } catch (_: IllegalArgumentException) {
            Result.Error(MalAuthError.UNKNOWN)
        }

    private suspend fun toResult(
        response: HttpResponse,
        rejectionIsDeadSession: Boolean,
    ): Result<MalTokens, MalAuthError> {
        val body = response.bodyAsText()
        return when {
            response.status.isSuccess() -> {
                val tokens = json.decodeFromString<TokenResponse>(body)
                Result.Success(MalTokens(tokens.accessToken, tokens.refreshToken, tokens.expiresIn))
            }

            response.status.value == TOO_MANY_REQUESTS ||
                response.status.value == FORBIDDEN ||
                response.status.value in REDIRECT_FROM..REDIRECT_UNTIL -> {
                Result.Error(MalAuthError.RATE_LIMITED)
            }

            response.status.value >= SERVER_ERROR_FROM -> {
                Result.Error(MalAuthError.SERVER)
            }

            rejectionIsDeadSession && response.status.value in BAD_REQUEST..UNAUTHORIZED -> {
                Result.Error(MalAuthError.INVALID_GRANT)
            }

            errorCode(body) == "invalid_grant" -> {
                Result.Error(MalAuthError.INVALID_GRANT)
            }

            else -> {
                Result.Error(MalAuthError.UNKNOWN)
            }
        }
    }

    private fun errorCode(body: String): String? =
        try {
            json.decodeFromString<ErrorResponse>(body).error
        } catch (_: SerializationException) {
            null
        }
}
