package de.lostmekka.sdwuic

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import de.lostmekka.sdwuic.components.ImageTileList
import de.lostmekka.sdwuic.components.Input
import de.lostmekka.sdwuic.components.Select
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

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
    val isInitialized = modelData != null && samplerData != null

    var working by remember { mutableStateOf(false) }
    var images by remember { mutableStateOf<List<ByteArray>>(listOf()) }

    var currGenerator by remember { mutableStateOf<Generator?>(null) }
    var generatorStatus by remember { mutableStateOf("waiting to start") }
    var progressStatus by remember { mutableStateOf<String?>(null) }
    var canUpdateGenerator by remember { mutableStateOf(false) }

    remember {
        CoroutineScope(Dispatchers.IO).launch {
            println("getting models")
            val currModel = async { Api.getCurrentModel() }
            val samplers = Api.getAvailableTxt2ImgSamplers()
            val models = Api.getAvailableModels()
            val model = currModel.await()
            samplerData = Triple(0, samplers.first(), samplers)
            modelData = Triple(models.indexOf(model), model, models)
        }
    }

    fun busyOperation(op: suspend () -> Unit) {
        working = true
        CoroutineScope(Dispatchers.IO).launch {
            op()
            working = false
        }
    }

    fun onProgress(progress: Progress) {
        if (progress.currentImage != null) images = listOf(progress.currentImage)
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
        )

    fun updateGenerator() {
        currGenerator?.config = generatorConfigFromCurrentState()
        canUpdateGenerator = false
    }

    MaterialTheme {
        Column {
            modelData
                ?.also { (index, _, models) ->
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
                onEnter = ::updateGenerator,
            )
            Input(
                label = "Negative prompt",
                value = negativePrompt,
                parser = { it },
                onChange = {
                    negativePrompt = it
                    canUpdateGenerator = true
                },
                onEnter = ::updateGenerator,
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
                        onEnter = ::updateGenerator,
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
                        onEnter = ::updateGenerator,
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
                        onEnter = ::updateGenerator,
                    )
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
            }
            if (images.isNotEmpty()) ImageTileList(images, 256.dp)
        }
    }
}
