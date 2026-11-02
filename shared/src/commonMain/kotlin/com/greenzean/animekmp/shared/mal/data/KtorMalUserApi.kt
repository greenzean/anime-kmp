package com.greenzean.animekmp.shared.mal.data

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.mal.domain.MalApiError
import com.greenzean.animekmp.shared.mal.domain.MalAuthError
import com.greenzean.animekmp.shared.mal.domain.MalAuthManager
import com.greenzean.animekmp.shared.mal.domain.MalUser
import com.greenzean.animekmp.shared.mal.domain.MalUserApi
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private const val USER_URL = "https://api.myanimelist.net/v2/users/@me"
private const val USER_NOT_FOUND = 404
private const val USER_FORBIDDEN = 403
private const val USER_TOO_MANY_REQUESTS = 429
private const val USER_REDIRECT_FROM = 300
private const val USER_REDIRECT_UNTIL = 399
private const val USER_SERVER_ERROR_FROM = 500

private val userJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class UserDto(
    val name: String,
)

class KtorMalUserApi(
    private val client: HttpClient,
    private val auth: MalAuthManager,
) : MalUserApi {
    override suspend fun me(): Result<MalUser, MalApiError> {
        val token =
            when (val result = auth.accessToken()) {
                is Result.Success -> result.data
                is Result.Error -> return Result.Error(result.error.toApiError())
            }
        return try {
            val response = client.get(USER_URL) { bearerAuth(token) }
            val status = response.status.value
            when {
                response.status.isSuccess() -> {
                    Result.Success(MalUser(userJson.decodeFromString<UserDto>(response.bodyAsText()).name))
                }

                status == USER_TOO_MANY_REQUESTS || status in USER_REDIRECT_FROM..USER_REDIRECT_UNTIL -> {
                    Result.Error(MalApiError.THROTTLED)
                }

                status == USER_FORBIDDEN -> {
                    Result.Error(MalApiError.FORBIDDEN)
                }

                status == USER_NOT_FOUND -> {
                    Result.Error(MalApiError.NOT_FOUND)
                }

                status >= USER_SERVER_ERROR_FROM -> {
                    Result.Error(MalApiError.SERVER)
                }

                else -> {
                    Result.Error(MalApiError.UNKNOWN)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            Result.Error(MalApiError.NO_CONNECTION)
        } catch (_: SerializationException) {
            Result.Error(MalApiError.SERIALIZATION)
        } catch (_: IllegalArgumentException) {
            Result.Error(MalApiError.SERIALIZATION)
        }
    }
}

internal fun MalAuthError.toApiError(): MalApiError =
    when (this) {
        MalAuthError.NO_INTERNET -> MalApiError.NO_CONNECTION
        MalAuthError.RATE_LIMITED -> MalApiError.THROTTLED
        MalAuthError.SERVER -> MalApiError.SERVER
        MalAuthError.NOT_LOGGED_IN, MalAuthError.INVALID_GRANT -> MalApiError.FORBIDDEN
        else -> MalApiError.UNKNOWN
    }
