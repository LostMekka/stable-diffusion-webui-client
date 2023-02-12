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
)

class Generator(
    var config: GeneratorConfig,
    private val maxBatchCount: Int? = null,
    private val resetBatchCountOnConfigChange: Boolean = true,
    private val onStatusChange: (String) -> Unit,
) {
    private var cancelled = false

    private val job = CoroutineScope(Dispatchers.IO).launch {
        File(Config.stagingDirPath).mkdirs()
        var currConfig: GeneratorConfig? = null
        var formattedConfigId = "??????"
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
                    formattedConfigId = writeConfigFile(nextConfig)
                    if (currConfig?.model != nextConfig.model) {
                        onStatusChange("loading model ${nextConfig.model}")
                        Api.setModel(nextConfig.model)
                    }
                    currConfig = nextConfig
                    batchIndex = 0
                    if (resetBatchCountOnConfigChange) totalBatchCount = 0
                }
                val fullFormattedBatchIndex = "${formattedConfigId}_${batchIndex.format(6)}"

                onStatusChange("generating batch $fullFormattedBatchIndex")
                val images = Api.generate(
                    Txt2ImgRequest(
                        prompt = currConfig.prompt,
                        negativePrompt = currConfig.negativePrompt,
                        width = currConfig.width,
                        height = currConfig.height,
                        sampler = currConfig.sampler,
                        steps = currConfig.steps,
                        batchSize = currConfig.batchSize,
                        cfgScale = currConfig.cfgScale,
                    )
                )

                onStatusChange("writing batch")
                for ((fileIndex, image) in images.withIndex()) {
                    // TODO: also include the individual file seed in the name
                    val imagePath = "${Config.stagingDirPath}/${fullFormattedBatchIndex}_$fileIndex.png"
                    File(imagePath).writeBytes(image)
                }

                batchIndex++
                totalBatchCount++
            }
        } catch (e: Exception) {
            onStatusChange("error: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun writeConfigFile(config: GeneratorConfig): String {
        val numberFile = File(Config.configIdFilePath)
        val number = if (numberFile.exists()) {
            numberFile.readText().toInt()
        } else {
            numberFile.parentFile.mkdirs()
            0
        }
        numberFile.writeText((number + 1).toString())
        val formattedNumber = number.format(6)
        File("${Config.stagingDirPath}/${formattedNumber}__config.txt").writeText(
            """
            config id       $formattedNumber
            prompt          ${config.prompt}
            negativePrompt  ${config.negativePrompt}
            sampler         ${config.sampler}
            model           ${config.model}
            width           ${config.width}
            height          ${config.height}
            steps           ${config.steps}
            batchSize       ${config.batchSize}
            cfgScale        ${config.cfgScale}
            """.trimIndent()
        )
        return formattedNumber
    }

    private fun Int.format(len: Int) = toString().padStart(len, '0')

    suspend fun close() {
        cancelled = true
        job.join()
    }
}
