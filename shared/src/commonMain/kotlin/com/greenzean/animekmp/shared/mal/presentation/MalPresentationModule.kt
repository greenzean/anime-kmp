package com.greenzean.animekmp.shared.mal.presentation

import com.greenzean.animekmp.shared.importer.domain.MergeMalEntryUseCase
import com.greenzean.animekmp.shared.mal.domain.AcceptMalConsentUseCase
import com.greenzean.animekmp.shared.mal.domain.BackgroundSyncUseCase
import com.greenzean.animekmp.shared.mal.domain.ConnectMalUseCase
import com.greenzean.animekmp.shared.mal.domain.DisconnectMalUseCase
import com.greenzean.animekmp.shared.mal.domain.DismissMalPromptUseCase
import com.greenzean.animekmp.shared.mal.domain.ImportMalListUseCase
import com.greenzean.animekmp.shared.mal.domain.LinkMalIfConnectedUseCase
import com.greenzean.animekmp.shared.mal.domain.ObserveMalAccountUseCase
import com.greenzean.animekmp.shared.mal.domain.ObserveMalImportUseCase
import com.greenzean.animekmp.shared.mal.domain.ObserveMalLinkStateUseCase
import com.greenzean.animekmp.shared.mal.domain.ObserveMalOutboxUseCase
import com.greenzean.animekmp.shared.mal.domain.ObserveMalPullUseCase
import com.greenzean.animekmp.shared.mal.domain.PullMalChangesUseCase
import com.greenzean.animekmp.shared.mal.domain.PullMalIfStaleUseCase
import com.greenzean.animekmp.shared.mal.domain.QueueAppOnlyEntriesUseCase
import com.greenzean.animekmp.shared.mal.domain.QueueMalChangeUseCase
import com.greenzean.animekmp.shared.mal.domain.RefreshLinkedPlatformsUseCase
import com.greenzean.animekmp.shared.mal.domain.RequestMalLinkUseCase
import com.greenzean.animekmp.shared.mal.domain.RetryMalSendUseCase
import com.greenzean.animekmp.shared.mal.domain.RetryPendingMalLinkUseCase
import com.greenzean.animekmp.shared.mal.domain.SendMalChangesUseCase
import com.greenzean.animekmp.shared.mal.domain.StartMalImportUseCase
import com.greenzean.animekmp.shared.mal.domain.SyncMalNowUseCase
import com.greenzean.animekmp.shared.mal.domain.UnlinkMalFromAccountUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val malPresentationModule =
    module {
        factoryOf(::ObserveMalAccountUseCase)
        factoryOf(::AcceptMalConsentUseCase)
        factoryOf(::ConnectMalUseCase)
        factoryOf(::DisconnectMalUseCase)
        factoryOf(::QueueMalChangeUseCase)
        factoryOf(::QueueAppOnlyEntriesUseCase)
        factoryOf(::SendMalChangesUseCase)
        factoryOf(::ObserveMalOutboxUseCase)
        factoryOf(::RetryMalSendUseCase)
        factoryOf(::ObserveMalImportUseCase)
        factoryOf(::StartMalImportUseCase)
        factoryOf(::ImportMalListUseCase)
        factoryOf(::MergeMalEntryUseCase)
        factoryOf(::PullMalChangesUseCase)
        factoryOf(::ObserveMalPullUseCase)
        factoryOf(::SyncMalNowUseCase)
        factoryOf(::PullMalIfStaleUseCase)
        factoryOf(::ObserveMalLinkStateUseCase)
        factoryOf(::RefreshLinkedPlatformsUseCase)
        factoryOf(::LinkMalIfConnectedUseCase)
        factoryOf(::BackgroundSyncUseCase)
        factoryOf(::RetryPendingMalLinkUseCase)
        factoryOf(::RequestMalLinkUseCase)
        factoryOf(::UnlinkMalFromAccountUseCase)
        factoryOf(::DismissMalPromptUseCase)
        viewModelOf(::MalAccountViewModel)
        viewModelOf(::MalImportViewModel)
    }
