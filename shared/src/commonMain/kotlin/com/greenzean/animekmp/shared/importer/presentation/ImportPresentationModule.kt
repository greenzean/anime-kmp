package com.greenzean.animekmp.shared.importer.presentation

import com.greenzean.animekmp.shared.importer.domain.FillImportedImagesUseCase
import com.greenzean.animekmp.shared.importer.domain.ImportMalFileUseCase
import com.greenzean.animekmp.shared.importer.domain.MalXmlParser
import com.greenzean.animekmp.shared.importer.domain.ReadImportFileUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val importPresentationModule =
    module {
        factoryOf(::MalXmlParser)
        factoryOf(::ReadImportFileUseCase)
        factoryOf(::ImportMalFileUseCase)
        factoryOf(::FillImportedImagesUseCase)
        viewModelOf(::ImportFileViewModel)
    }
