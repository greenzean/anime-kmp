package com.greenzean.animekmp.shared.auth.presentation

import com.greenzean.animekmp.shared.auth.data.ApolloAuthApi
import com.greenzean.animekmp.shared.auth.domain.AuthApi
import com.greenzean.animekmp.shared.auth.domain.DeleteAccountUseCase
import com.greenzean.animekmp.shared.auth.domain.MergeLibraryUseCase
import com.greenzean.animekmp.shared.auth.domain.SignInWithGoogleUseCase
import com.greenzean.animekmp.shared.auth.domain.SignOutUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val authModule =
    module {
        single<AuthApi> { ApolloAuthApi(get()) }
        factoryOf(::SignInWithGoogleUseCase)
        factoryOf(::MergeLibraryUseCase)
        factoryOf(::SignOutUseCase)
        factoryOf(::DeleteAccountUseCase)
        viewModelOf(::AccountViewModel)
    }
