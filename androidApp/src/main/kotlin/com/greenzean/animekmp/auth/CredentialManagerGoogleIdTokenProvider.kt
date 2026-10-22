package com.greenzean.animekmp.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.greenzean.animekmp.shared.auth.domain.GoogleIdTokenProvider
import com.greenzean.animekmp.shared.auth.domain.GoogleSignInError
import com.greenzean.animekmp.shared.core.domain.Result

/** [serverClientId] is the web OAuth client ID: the ID token is issued for our server, which checks `aud`. */
class CredentialManagerGoogleIdTokenProvider(
    private val activities: CurrentActivityHolder,
    private val serverClientId: String,
) : GoogleIdTokenProvider {
    override suspend fun getIdToken(nonce: String): Result<String, GoogleSignInError> {
        val activity = activities.current
        return when {
            serverClientId.isBlank() -> Result.Error(GoogleSignInError.NOT_CONFIGURED)
            activity == null -> Result.Error(GoogleSignInError.UNKNOWN)
            else -> request(activity, nonce)
        }
    }

    private suspend fun request(
        activity: Activity,
        nonce: String,
    ): Result<String, GoogleSignInError> {
        val request =
            GetCredentialRequest
                .Builder()
                .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).setNonce(nonce).build())
                .build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                Result.Success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                Result.Error(GoogleSignInError.UNKNOWN)
            }
        } catch (_: GetCredentialCancellationException) {
            Result.Error(GoogleSignInError.CANCELLED)
        } catch (_: NoCredentialException) {
            Result.Error(GoogleSignInError.NO_CREDENTIAL)
        } catch (_: GoogleIdTokenParsingException) {
            Result.Error(GoogleSignInError.UNKNOWN)
        } catch (_: GetCredentialException) {
            Result.Error(GoogleSignInError.UNKNOWN)
        }
    }
}
