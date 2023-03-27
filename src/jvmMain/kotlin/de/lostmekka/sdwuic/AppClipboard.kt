package de.lostmekka.sdwuic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.ClipboardOwner
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import javax.imageio.ImageIO
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

object AppClipboard {
    private object Owner : ClipboardOwner {
        override fun lostOwnership(clipboard: Clipboard, contents: Transferable) = Unit
    }

    private val clipboard by lazy { Toolkit.getDefaultToolkit().systemClipboard }

    @OptIn(ExperimentalTime::class)
    fun copy(imageData: ByteArray) {
        CoroutineScope(Dispatchers.IO).launch {
            val time = measureTime {
                clipboard.setContents(TransferableImage(imageData), Owner)
            }
            println("copy to clipboard took $time")
        }
    }

    private class TransferableImage(private val imageData: ByteArray) : Transferable {
        override fun getTransferDataFlavors() = arrayOf(DataFlavor.imageFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.imageFlavor
        override fun getTransferData(flavor: DataFlavor): Any = ImageIO.read(imageData.inputStream())
    }
}
