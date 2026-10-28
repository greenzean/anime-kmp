package com.greenzean.animekmp.composeapp.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.shared.mal.presentation.MalAccountError
import com.greenzean.animekmp.shared.mal.presentation.MalAccountStatus

@Preview
@Composable
private fun MalAccountConnectedLightPreview() = MalAccountPreview(MalAccountStatus.Connected("francisco"), false)

@Preview
@Composable
private fun MalAccountConnectedDarkPreview() = MalAccountPreview(MalAccountStatus.Connected("francisco"), true)

@Preview
@Composable
private fun MalAccountConnectedNoNameLightPreview() = MalAccountPreview(MalAccountStatus.Connected(null), false)

@Preview
@Composable
private fun MalAccountErrorLightPreview() = MalAccountPreview(MalAccountStatus.Error(MalAccountError.NoInternet), false)

@Preview
@Composable
private fun MalAccountErrorDarkPreview() = MalAccountPreview(MalAccountStatus.Error(MalAccountError.NoInternet), true)
