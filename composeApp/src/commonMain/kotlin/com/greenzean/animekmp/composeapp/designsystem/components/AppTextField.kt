package com.greenzean.animekmp.composeapp.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview

/** Outlined text field with a visible label and optional error text (announced to screen readers). */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { if (errorText != null) error(errorText) },
        label = { Text(label) },
        isError = errorText != null,
        supportingText = errorText?.let { { Text(it) } },
        singleLine = singleLine,
        shape = MaterialTheme.shapes.medium,
    )
}

@Preview
@Composable
private fun AppTextFieldLightPreview() = AppTextFieldPreview(darkTheme = false)

@Preview
@Composable
private fun AppTextFieldDarkPreview() = AppTextFieldPreview(darkTheme = true)

@Composable
private fun AppTextFieldPreview(darkTheme: Boolean) {
    ThemedPreview(darkTheme) {
        Column(Modifier.padding(16.dp)) {
            AppTextField(value = "Frieren", onValueChange = {}, label = "Search anime")
            AppTextField(value = "", onValueChange = {}, label = "Title", errorText = "Enter a title")
        }
    }
}
