package de.lostmekka.sdwuic

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import de.lostmekka.sdwuic.components.Input
import de.lostmekka.sdwuic.components.Select
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image
import kotlin.math.roundToInt

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        App()
    }
}

@Composable
@Preview
fun App() {
    var prompt by remember { mutableStateOf("") }
    var negativePrompt by remember { mutableStateOf("") }
    var modelData by remember { mutableStateOf<Triple<Int, String, List<String>>?>(null) }
    var samplerData by remember { mutableStateOf<Triple<Int, String, List<String>>?>(null) }
    var steps by remember { mutableStateOf(25) }
    var batchSize by remember { mutableStateOf(4) }
    var cfgScale by remember { mutableStateOf(7f) }
    var useTiling by remember { mutableStateOf(false) }
    val isInitialized = modelData != null && samplerData != null

    var working by remember { mutableStateOf(false) }
    var images by remember { mutableStateOf<List<ByteArray>>(listOf()) }
    var imagesPerRow by remember { mutableStateOf(4f) }

    var currGenerator by remember { mutableStateOf<Generator?>(null) }
    var generatorStatus by remember { mutableStateOf("waiting to start") }
    var progressStatus by remember { mutableStateOf<String?>(null) }
    var canUpdateGenerator by remember { mutableStateOf(false) }

    fun busyOperation(op: suspend () -> Unit) {
        working = true
        CoroutineScope(Dispatchers.IO).launch {
            op()
            working = false
        }
    }

    fun onProgress(progress: Progress) {
        if (progress.currentImages != null) images = progress.currentImages
        progressStatus = "%.2f%%".format(progress.progress * 100)
    }

    fun generate(batchSize: Int) {
        busyOperation {
            Api.setModel(modelData!!.second)
            images = Api.generate(
                request = Txt2ImgRequest(
                    prompt = prompt,
                    negativePrompt = negativePrompt,
                    sampler = samplerData!!.second,
                    width = 512,
                    height = 512,
                    steps = steps,
                    batchSize = batchSize,
                    cfgScale = cfgScale,
                    tiling = useTiling,
                ),
                progressDelayInMs = 1000,
                onProgress = ::onProgress,
            )
            progressStatus = null
        }
    }

    fun generatorConfigFromCurrentState() =
        GeneratorConfig(
            prompt = prompt,
            sampler = samplerData!!.second, // TODO: make sure samplers are loaded at this point
            model = modelData!!.second, // TODO: make sure models are loaded at this point
            negativePrompt = negativePrompt,
            steps = steps,
            batchSize = batchSize,
            cfgScale = cfgScale,
            tiling = useTiling,
        )

    fun onEnterPressedInTextInput() {
        val gen = currGenerator
        if (gen == null) {
            if (!working) generate(batchSize)
        } else {
            gen.config = generatorConfigFromCurrentState()
            canUpdateGenerator = false
        }
    }

    fun saveToDisk(image: ByteArray) {
        CoroutineScope(Dispatchers.IO).launch {
            ImageWriter.writeImage(generatorConfigFromCurrentState(), image, newBatch = false)
        }
    }

    fun refreshModels() {
        modelData = null
        CoroutineScope(Dispatchers.IO).launch {
            println("getting models")
            val samplersJob = async { Api.getAvailableSamplers() }
            val modelsJob = async { Api.getAvailableModels() }
            val currModelJob = async { Api.getCurrentModel() }
            val samplers = samplersJob.await()
            val models = modelsJob.await()
            val currModel = currModelJob.await()
            samplerData = Triple(0, samplerData?.second ?: samplers.first(), samplers)
            modelData = Triple(models.indexOf(currModel), currModel, models)
        }
    }

    remember { refreshModels() }

    MaterialTheme {
        Column {
            modelData
                ?.also { (index, _, models) ->
                    ContextMenuArea(items = { listOf(ContextMenuItem("Refresh") { refreshModels() }) }) {
                        Select(index, models) { newIndex, newModel ->
                            if (modelData?.second != newModel) {
                                if (currGenerator == null) {
                                    modelData = null
                                    CoroutineScope(Dispatchers.IO).launch {
                                        Api.setModel(newModel)
                                        modelData = Triple(newIndex, newModel, models)
                                    }
                                } else {
                                    modelData = Triple(newIndex, newModel, models)
                                    canUpdateGenerator = true
                                }
                            }
                        }
                    }
                }
                ?: CircularProgressIndicator()
            samplerData
                ?.also { (index, _, samplers) ->
                    Select(index, samplers) { newIndex, newSampler ->
                        samplerData = Triple(newIndex, newSampler, samplers)
                        canUpdateGenerator = true
                    }
                }
                ?: CircularProgressIndicator()
            Input(
                label = "Positive prompt",
                value = prompt,
                parser = { it },
                onChange = {
                    prompt = it
                    canUpdateGenerator = true
                },
                onEnter = ::onEnterPressedInTextInput,
            )
            Input(
                label = "Negative prompt",
                value = negativePrompt,
                parser = { it },
                onChange = {
                    negativePrompt = it
                    canUpdateGenerator = true
                },
                onEnter = ::onEnterPressedInTextInput,
            )
            Row {
                Column(modifier = Modifier.weight(1f)) {
                    Input(
                        label = "Batch size",
                        value = batchSize,
                        parser = { value -> value.toIntOrNull()?.takeIf { it in 1..16 } },
                        onChange = {
                            batchSize = it
                            canUpdateGenerator = true
                        },
                        onEnter = ::onEnterPressedInTextInput,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Input(
                        label = "Steps",
                        value = steps,
                        parser = { value -> value.toIntOrNull()?.takeIf { it in 1..100 } },
                        onChange = {
                            steps = it
                            canUpdateGenerator = true
                        },
                        onEnter = ::onEnterPressedInTextInput,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Input(
                        label = "Cfg scale",
                        value = cfgScale,
                        parser = { value -> value.toFloatOrNull()?.takeIf { it in 1f..30f } },
                        onChange = {
                            cfgScale = it
                            canUpdateGenerator = true
                        },
                        onEnter = ::onEnterPressedInTextInput,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row {
                        Checkbox(
                            checked = useTiling,
                            onCheckedChange = {
                                useTiling = it
                                canUpdateGenerator = true
                            },
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                        Text("Tiling", modifier = Modifier.align(Alignment.CenterVertically))
                    }
                }
            }
            Row {
                Button(
                    onClick = {
                        currGenerator = Generator(
                            config = generatorConfigFromCurrentState(),
                            onStatusChange = { generatorStatus = it },
                            onProgress = ::onProgress,
                        )
                        canUpdateGenerator = false
                    },
                    enabled = currGenerator == null && isInitialized,
                    content = {
                        Icon(Icons.Default.PlayArrow, null)
                        Text("start generator")
                    },
                )
                Button(
                    onClick = {
                        currGenerator?.config = generatorConfigFromCurrentState()
                        canUpdateGenerator = false
                    },
                    enabled = currGenerator != null && canUpdateGenerator,
                    content = {
                        Icon(Icons.Default.Refresh, null)
                        Text("update generator config")
                    },
                )
                Button(
                    onClick = {
                        // TODO: prevent multiple clicks
                        currGenerator?.let {
                            CoroutineScope(Dispatchers.IO).launch {
                                it.close()
                                currGenerator = null
                                progressStatus = null
                            }
                        }
                    },
                    enabled = currGenerator != null,
                    content = {
                        Icon(Icons.Default.Close, null)
                        Text("stop generator")
                    },
                )
            }
            Row {
                Text("Generator status: $generatorStatus")
                Text(progressStatus?.let { "($it)" } ?: "")
            }
            Row {
                Button(
                    onClick = { generate(1) },
                    enabled = !working && currGenerator == null && isInitialized,
                    content = {
                        Icon(Icons.Default.Search, null)
                        Text("generate single image")
                    },
                )
                Button(
                    onClick = { generate(batchSize) },
                    enabled = !working && currGenerator == null && isInitialized,
                    content = {
                        Icon(Icons.Default.Search, null)
                        Text("generate single batch")
                    },
                )
                Column {
                    Text("Preview images per row: ${imagesPerRow.roundToInt()}")
                    Slider(
                        value = imagesPerRow,
                        onValueChange = { imagesPerRow = it },
                        valueRange = 1f..10f,
                        steps = 8,
                    )
                }
            }
            if (images.isNotEmpty()) LazyColumn {
                for (chunk in images.chunked(imagesPerRow.roundToInt())) item {
                    Row {
                        for (image in chunk) Column(modifier = Modifier.weight(1f)) {
                            ContextMenuArea(
                                items = {
                                    listOf(
                                        ContextMenuItem("Write to disk") { saveToDisk(image) },
                                        ContextMenuItem("Copy to clipboard") { AppClipboard.copy(image) },
                                    )
                                },
                            ) {
                                Image(
                                    bitmap = Image.makeFromEncoded(image).toComposeImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
