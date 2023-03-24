package de.lostmekka.sdwuic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private val darkColors = darkColors(
    primary = Color(0xff365880),
    primaryVariant = Color(0xff365880),
    onPrimary = Color(0xffBBBBBB),

    secondary = Color(0xff4c5052),
    secondaryVariant = Color(0xff4c5052),
    onSecondary = Color(0xffBBBBBB),

    background = Color(0xff181818),
    onBackground = Color(0xffa2b4c4),

    surface = Color(0xff3c3f41),
    onSurface = Color(0xff777777),

    error = Color(0xff560200),
    onError = Color(0xffBC3F3C),
)

private val lightColors = lightColors()

@Composable
fun Themed(content: @Composable () -> Unit) {
    // TODO: put theme toggle switch somewhere
    var colors by remember { mutableStateOf(darkColors) }
    var isDark by remember { mutableStateOf(true) }
    fun changeColor(dark: Boolean) {
        if (isDark == dark) return
        isDark = dark
        colors = if (dark) darkColors else lightColors
    }
    val selectionColors = TextSelectionColors(
        handleColor = MaterialTheme.colors.primary,
        backgroundColor = MaterialTheme.colors.primary,
    )
    MaterialTheme(colors = colors) {
        CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
                content()
            }
        }
    }
}
