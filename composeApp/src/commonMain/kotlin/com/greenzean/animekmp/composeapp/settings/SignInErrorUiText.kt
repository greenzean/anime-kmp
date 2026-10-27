package com.greenzean.animekmp.composeapp.settings

import com.greenzean.animekmp.composeapp.core.UiText
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.delete_error_unauthorized
import com.greenzean.animekmp.composeapp.generated.resources.sign_in_error_google_failed
import com.greenzean.animekmp.composeapp.generated.resources.sign_in_error_no_connection
import com.greenzean.animekmp.composeapp.generated.resources.sign_in_error_no_credential
import com.greenzean.animekmp.composeapp.generated.resources.sign_in_error_not_configured
import com.greenzean.animekmp.composeapp.generated.resources.sign_in_error_server
import com.greenzean.animekmp.composeapp.generated.resources.sign_in_error_unauthorized
import com.greenzean.animekmp.shared.auth.domain.GoogleSignInError
import com.greenzean.animekmp.shared.auth.domain.SignInError
import com.greenzean.animekmp.shared.core.domain.DataError

internal fun SignInError.toUiText(): UiText =
    when (this) {
        is SignInError.Google -> {
            reason.toUiText()
        }

        is SignInError.Server -> {
            when (reason) {
                DataError.Network.NO_INTERNET, DataError.Network.REQUEST_TIMEOUT -> {
                    UiText.StringResource(Res.string.sign_in_error_no_connection)
                }

                DataError.Network.UNAUTHORIZED -> {
                    UiText.StringResource(Res.string.sign_in_error_unauthorized)
                }

                else -> {
                    UiText.StringResource(Res.string.sign_in_error_server)
                }
            }
        }
    }

internal fun SignInError.toDeleteUiText(): UiText =
    if (this is SignInError.Server && reason == DataError.Network.UNAUTHORIZED) {
        UiText.StringResource(Res.string.delete_error_unauthorized)
    } else {
        toUiText()
    }

private fun GoogleSignInError.toUiText(): UiText =
    when (this) {
        GoogleSignInError.NO_CREDENTIAL -> {
            UiText.StringResource(Res.string.sign_in_error_no_credential)
        }

        GoogleSignInError.NOT_CONFIGURED -> {
            UiText.StringResource(Res.string.sign_in_error_not_configured)
        }

        GoogleSignInError.CANCELLED, GoogleSignInError.UNKNOWN -> {
            UiText.StringResource(
                Res.string.sign_in_error_google_failed,
            )
        }
    }
