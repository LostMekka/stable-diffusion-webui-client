package de.lostmekka.sdwuic.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun <T: Any> Input(
    label: String,
    value: T,
    parser: (String) -> T?,
    onChange: (T) -> Unit,
    onEnter: () -> Unit = {},
) {
    var rawValue by remember { mutableStateOf(value.toString()) }
    var isValid by remember { mutableStateOf(true) }
    TextField(
        value = rawValue,
        label = { Text(label) },
        onValueChange = {
            rawValue = it
            val parsedValue = parser(it)
            if (parsedValue != null) onChange(parsedValue)
            isValid = parsedValue != null
        },
        modifier = Modifier
            .fillMaxWidth()
            .onPreviewKeyEvent {
                when {
                    it.isShiftPressed -> false
                    it.key != Key.Enter -> false
                    it.type != KeyEventType.KeyUp -> true
                    else -> true.also { if (isValid) onEnter() }
                }
            }
            .run { if (isValid) this else background(Color.Red) },
    )
}
