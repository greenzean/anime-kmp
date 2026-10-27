package com.greenzean.animekmp.composeapp.details

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.shared.details.presentation.DetailsState
import com.greenzean.animekmp.shared.details.presentation.DetailsStatus

@Preview
@Composable
private fun DetailsOfflineLightPreview() =
    DetailsPreview(DetailsState(status = DetailsStatus.Offline), darkTheme = false)

@Preview
@Composable
private fun DetailsOfflineDarkPreview() = DetailsPreview(DetailsState(status = DetailsStatus.Offline), darkTheme = true)

@Preview
@Composable
private fun DetailsErrorLightPreview() = DetailsPreview(DetailsState(status = DetailsStatus.Error), darkTheme = false)

@Preview
@Composable
private fun DetailsErrorDarkPreview() = DetailsPreview(DetailsState(status = DetailsStatus.Error), darkTheme = true)

@Preview
@Composable
private fun DetailsNotFoundLightPreview() =
    DetailsPreview(DetailsState(status = DetailsStatus.NotFound), darkTheme = false)

@Preview
@Composable
private fun DetailsNotFoundDarkPreview() =
    DetailsPreview(DetailsState(status = DetailsStatus.NotFound), darkTheme = true)

@Preview
@Composable
private fun DetailsThrottledLightPreview() =
    DetailsPreview(DetailsState(status = DetailsStatus.Throttled), darkTheme = false)

@Preview
@Composable
private fun DetailsThrottledDarkPreview() =
    DetailsPreview(DetailsState(status = DetailsStatus.Throttled), darkTheme = true)
