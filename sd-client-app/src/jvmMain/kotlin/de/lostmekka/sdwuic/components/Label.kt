package de.lostmekka.sdwuic.components

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text, color = MaterialTheme.colors.onBackground, modifier = modifier)
}
