package com.greenzean.animekmp.shared.export.presentation

import com.greenzean.animekmp.shared.export.domain.ExportLibraryToMalUseCase
import com.greenzean.animekmp.shared.export.domain.MalXmlWriter
import com.greenzean.animekmp.shared.export.domain.SaveExportFileUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val exportPresentationModule =
    module {
        factoryOf(::MalXmlWriter)
        factoryOf(::ExportLibraryToMalUseCase)
        factoryOf(::SaveExportFileUseCase)
        viewModelOf(::ExportViewModel)
    }
