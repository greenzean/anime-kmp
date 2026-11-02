package com.greenzean.animekmp.shared.mal.data

import com.greenzean.animekmp.shared.mal.domain.MalAnimeApi
import com.greenzean.animekmp.shared.mal.domain.MalAnimeCache
import com.greenzean.animekmp.shared.mal.domain.MalAnimeRepository
import com.greenzean.animekmp.shared.mal.domain.MalAuthApi
import com.greenzean.animekmp.shared.mal.domain.MalAuthManager
import com.greenzean.animekmp.shared.mal.domain.MalRedirectHandler
import com.greenzean.animekmp.shared.mal.domain.MalSuggestionsApi
import com.greenzean.animekmp.shared.mal.domain.MalSuggestionsRepository
import com.greenzean.animekmp.shared.mal.domain.MalThrottle
import com.greenzean.animekmp.shared.mal.domain.MalUserApi
import io.ktor.client.HttpClient
import org.koin.dsl.module

val malModule =
    module {
        single<MalAuthApi> { KtorMalAuthApi(HttpClient { followRedirects = false }, get(), get()) }
        single { MalAuthManager(get(), get(), get(), get(), get(), logger = get()) }
        single { MalRedirectHandler(get()) }
        single<MalAnimeApi> { KtorMalAnimeApi(HttpClient { followRedirects = false }, get(), get()) }
        single<MalSuggestionsApi> { KtorMalSuggestionsApi(HttpClient { followRedirects = false }, get(), get()) }
        single<MalUserApi> { KtorMalUserApi(HttpClient { followRedirects = false }, get(), get()) }
        single<MalAnimeCache> { SqlDelightMalCache(get()) }
        single { MalThrottle(logger = get()) }
        single { MalAnimeRepository(get(), get(), get(), logger = get()) }
        single { MalSuggestionsRepository(get(), get(), get(), logger = get()) }
    }
