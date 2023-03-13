package de.lostmekka.sdwuic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

data class GeneratorConfig(
    val prompt: String,
    val sampler: String,
    val model: String,
    val negativePrompt: String = "",
    val width: Int = 512,
    val height: Int = 512,
    val steps: Int = 25,
    val batchSize: Int = 4,
    val cfgScale: Float = 7f,
    val tiling: Boolean = false,
)

class Generator(
    var config: GeneratorConfig,
    private val maxBatchCount: Int? = null,
    private val resetBatchCountOnConfigChange: Boolean = true,
    private val onStatusChange: (String) -> Unit,
    private val onProgress: (Progress) -> Unit,
) {
    private var cancelled = false

    private val job = CoroutineScope(Dispatchers.IO).launch {
        File(Config.stagingDirPath).mkdirs()
        var currConfig: GeneratorConfig? = null
        var batchIndex = 0
        var totalBatchCount = 0
        try {
            while (true) {
                if (cancelled) {
                    onStatusChange("cancelled after $totalBatchCount batches")
                    break
                }
                if (maxBatchCount != null && totalBatchCount > maxBatchCount) {
                    onStatusChange("finished after $totalBatchCount batches")
                    break
                }

                val nextConfig = config
                if (nextConfig.copy(batchSize = currConfig?.batchSize ?: -1) != currConfig) {
                    if (currConfig?.model != nextConfig.model) {
                        onStatusChange("loading model ${nextConfig.model}")
                        Api.setModel(nextConfig.model)
                    }
                    currConfig = nextConfig
                    batchIndex = 0
                    if (resetBatchCountOnConfigChange) totalBatchCount = 0
                }
                val fullFormattedBatchIndex = ImageWriter.buildFullBatchName(batchIndex)

                onStatusChange("generating batch $fullFormattedBatchIndex")

                val images = Api.generate(
                    request = Txt2ImgRequest(
                        prompt = currConfig.prompt,
                        negativePrompt = currConfig.negativePrompt,
                        width = currConfig.width,
                        height = currConfig.height,
                        sampler = currConfig.sampler,
                        steps = currConfig.steps,
                        batchSize = currConfig.batchSize,
                        cfgScale = currConfig.cfgScale,
                        tiling = currConfig.tiling,
                    ),
                    progressDelayInMs = 1000,
                    onProgress = onProgress,
                )

                onStatusChange("writing batch")
                for ((fileIndex, image) in images.withIndex()) {
                    // TODO: also include the individual file seed in the name
                    ImageWriter.writeImage(config, image, fileIndex == 0)
                }

                batchIndex++
                totalBatchCount++
                onProgress(Progress(1f, 0f, images))
            }
        } catch (e: Exception) {
            onStatusChange("error: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun close() {
        cancelled = true
        job.join()
    }
}
