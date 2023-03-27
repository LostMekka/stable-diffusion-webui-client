package de.lostmekka.sdwuic

import java.io.File

object ImageWriter {
    private val numberFile = File(Config.configIdFilePath)
    private var currConfig: GeneratorConfig? = null
    private var currConfigIndex = readNumberFile(increment = false)
    private var currBatchIndex = 0
    private var currImageIndex = 0

    suspend fun writeImage(config: GeneratorConfig, image: ByteArray, newBatch: Boolean) {
        // TODO: prevent parallel calls
        if (config != currConfig) {
            currConfig = config
            currConfigIndex = writeConfigFile(config)
            currBatchIndex = 0
            currImageIndex = 0
        } else if (newBatch) {
            currBatchIndex++
            currImageIndex = 0
        }
        val fullFormattedBatchIndex = "${currConfigIndex}_${currBatchIndex.format(6)}"
        val imagePath = "${Config.stagingDirPath}/${fullFormattedBatchIndex}_$currImageIndex.png"
        File(imagePath).writeBytes(image)
        currImageIndex++
    }

    private fun writeConfigFile(config: GeneratorConfig): String {
        val formattedNumber = readNumberFile(increment = true)
        File("${Config.stagingDirPath}/${formattedNumber}__config.txt").writeText(
            """
            configID        $formattedNumber
            prompt          ${config.prompt}
            negativePrompt  ${config.negativePrompt}
            sampler         ${config.sampler}
            model           ${config.model}
            width           ${config.width}
            height          ${config.height}
            steps           ${config.steps}
            batchSize       ${config.batchSize}
            cfgScale        ${config.cfgScale}
            tiling          ${config.tiling}
            """.trimIndent(),
        )
        return formattedNumber
    }

    private fun readNumberFile(increment: Boolean): String {
        val number = if (numberFile.exists()) {
            numberFile.readText().toInt()
        } else {
            numberFile.parentFile.mkdirs()
            0
        }
        if (increment) numberFile.writeText((number + 1).toString())
        return formatConfigIndex(number)
    }

    fun buildFullBatchName(batchIndex: Int) = "${currConfigIndex}_${formatBatchIndex(batchIndex)}"

    private fun formatConfigIndex(number: Int) = number.format(6)
    private fun formatBatchIndex(number: Int) = number.format(6)

    private fun Int.format(len: Int) = toString().padStart(len, '0')
}
