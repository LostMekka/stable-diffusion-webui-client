package de.lostmekka.sdwuic.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Image

@Composable
fun ImageTileList(images: List<ByteArray>, minSize: Dp = 256.dp) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minSize),
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.SpaceAround,
    ) {
        items(images) { image ->
            Image(
                bitmap = Image.makeFromEncoded(image).toComposeImageBitmap(),
                contentDescription = null,
            )
        }
    }
}
