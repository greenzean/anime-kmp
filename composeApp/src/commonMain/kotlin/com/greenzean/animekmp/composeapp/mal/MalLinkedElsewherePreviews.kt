package com.greenzean.animekmp.composeapp.mal

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.shared.mal.presentation.MalAccountStatus

@Preview
@Composable
private fun MalConnectionLinkedElsewhereLightPreview() =
    MalConnectionPreview(MalAccountStatus.NotConnected, false, linkedElsewhere = true)

@Preview
@Composable
private fun MalConnectionLinkedElsewhereDarkPreview() =
    MalConnectionPreview(MalAccountStatus.NotConnected, true, linkedElsewhere = true)
