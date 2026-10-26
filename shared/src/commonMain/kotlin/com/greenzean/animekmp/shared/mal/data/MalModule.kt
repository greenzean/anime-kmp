package com.greenzean.animekmp.shared.mal.data

import com.greenzean.animekmp.shared.mal.domain.MalAuthApi
import com.greenzean.animekmp.shared.mal.domain.MalAuthManager
import com.greenzean.animekmp.shared.mal.domain.MalRedirectHandler
import io.ktor.client.HttpClient
import org.koin.dsl.module

val malModule =
    module {
        single<MalAuthApi> { KtorMalAuthApi(HttpClient { followRedirects = false }, get()) }
        single { MalAuthManager(get(), get(), get(), get(), get()) }
        single { MalRedirectHandler(get()) }
    }
