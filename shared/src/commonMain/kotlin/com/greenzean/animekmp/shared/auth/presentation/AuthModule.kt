package com.greenzean.animekmp.shared.auth.presentation

import com.greenzean.animekmp.shared.auth.data.ApolloAuthApi
import com.greenzean.animekmp.shared.auth.domain.AuthApi
import com.greenzean.animekmp.shared.auth.domain.SignInWithGoogleUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** `GoogleIdTokenProvider` is bound by the platform (androidApp). */
val authModule =
    module {
        single<AuthApi> { ApolloAuthApi(get()) }
        factoryOf(::SignInWithGoogleUseCase)
        viewModelOf(::AccountViewModel)
    }
