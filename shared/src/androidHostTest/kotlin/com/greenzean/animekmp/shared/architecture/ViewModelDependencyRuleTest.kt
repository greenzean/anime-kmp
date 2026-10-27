package com.greenzean.animekmp.shared.architecture

import com.lemonappdev.konsist.api.Konsist
import kotlin.test.Test
import kotlin.test.assertTrue

class ViewModelDependencyRuleTest {
    @Test
    fun viewModelConstructorsTakeNoRepositoryOrDataSource() {
        val viewModels =
            Konsist
                .scopeFromProduction()
                .classes()
                .filter { it.name.endsWith("ViewModel") }
        assertTrue(viewModels.isNotEmpty())

        val offenders =
            viewModels
                .flatMap { viewModel ->
                    viewModel.primaryConstructor
                        ?.parameters
                        .orEmpty()
                        .filter { it.type.name.endsWith("Repository") || it.type.name.endsWith("DataSource") }
                        .map { "${viewModel.name}(${it.name}: ${it.type.name})" }
                }
        assertTrue(offenders.isEmpty(), "View models must depend on use cases: $offenders")
    }
}
