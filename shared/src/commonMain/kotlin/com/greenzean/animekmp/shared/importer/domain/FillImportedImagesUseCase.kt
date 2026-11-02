package com.greenzean.animekmp.shared.importer.domain

import com.greenzean.animekmp.shared.sync.domain.ParkedAnimeResolver

class FillImportedImagesUseCase(
    private val resolver: ParkedAnimeResolver,
) {
    suspend operator fun invoke() = resolver.resolve()
}
