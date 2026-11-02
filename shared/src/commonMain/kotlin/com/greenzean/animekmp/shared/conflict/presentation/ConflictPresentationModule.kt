package com.greenzean.animekmp.shared.conflict.presentation

import com.greenzean.animekmp.shared.conflict.data.SqlDelightConflictDataSource
import com.greenzean.animekmp.shared.conflict.domain.ConflictLocalDataSource
import com.greenzean.animekmp.shared.conflict.domain.ObserveConflictCountUseCase
import com.greenzean.animekmp.shared.conflict.domain.ObserveConflictsUseCase
import com.greenzean.animekmp.shared.conflict.domain.ResolveAllConflictsUseCase
import com.greenzean.animekmp.shared.conflict.domain.ResolveConflictUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val conflictModule =
    module {
        single<ConflictLocalDataSource> { SqlDelightConflictDataSource(get()) }
        factoryOf(::ObserveConflictsUseCase)
        factoryOf(::ObserveConflictCountUseCase)
        factoryOf(::ResolveConflictUseCase)
        factoryOf(::ResolveAllConflictsUseCase)
        viewModelOf(::ConflictsViewModel)
    }
