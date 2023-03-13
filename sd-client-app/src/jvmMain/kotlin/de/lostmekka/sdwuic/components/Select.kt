package de.lostmekka.sdwuic.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun Select(selectedIndex: Int, availableValues: List<String>, onChange: (Int, String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier
        .fillMaxWidth()
        .height(20.dp)
        .wrapContentSize(Alignment.TopStart)
    ) {
        Text(
            text = availableValues[selectedIndex],
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .background(Color.Gray)
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.8f),
        ) {
            for ((i, value) in availableValues.withIndex()) {
                DropdownMenuItem(
                    onClick = {
                        expanded = false
                        onChange(i, value)
                    },
                ) {
                    Text(value)
                }
            }
        }
    }
}
