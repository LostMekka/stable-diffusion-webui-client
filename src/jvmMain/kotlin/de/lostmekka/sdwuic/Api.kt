package de.lostmekka.sdwuic

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.core.await
import com.github.kittinunf.fuel.jackson.jacksonDeserializerOf
import com.github.kittinunf.fuel.jackson.objectBody
import com.github.kittinunf.fuel.util.decodeBase64
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.Reader
import java.util.*
import javax.imageio.ImageIO

private suspend fun <T> retry(opName: String, block: suspend () -> T): T {
    while (true) {
        try {
            return block()
        } catch (e: Exception) {
            println("error in operation $opName: ${e.message}")
            delay(1000)
        }
    }
}

object Api {
    private var availableModels: List<String>? = null
    suspend fun getAvailableModels(forceReload: Boolean = false): List<String> {
        val currModels = availableModels
        if (currModels != null && !forceReload) return currModels
        data class Model(val title: String)
        return retry("get models") {
            Fuel.get("${Config.apiBasePath}/sdapi/v1/sd-models")
                .await<List<Model>>()
                .map { it.title }
                .sortedBy { it.lowercase() }
                .also { availableModels = it }
        }
    }

    private var availableSamplers: List<String>? = null
    suspend fun getAvailableSamplers(forceReload: Boolean = false): List<String> {
        val cache = availableSamplers
        if (cache != null && !forceReload) return cache
        data class Sampler(val name: String)
        return retry("get samplers") {
            Fuel.get("${Config.apiBasePath}/sdapi/v1/samplers")
                .await<List<Sampler>>()
                .map { it.name }
                .also { availableSamplers = it }
        }
    }

    private var currentModel: String? = null
    suspend fun getCurrentModel(): String {
        return currentModel ?: retry("get current model") {
            Fuel.get("${Config.apiBasePath}/sdapi/v1/options")
                .await<Map<String, Any>>()
                .let { it["sd_model_checkpoint"] as String }
                .also { currentModel = it }
        }
    }

    suspend fun setModel(newModel: String): String {
        require(newModel in getAvailableModels())
        if (newModel == currentModel) return newModel
        Fuel.post("${Config.apiBasePath}/sdapi/v1/options")
            .objectBody(mapOf("sd_model_checkpoint" to newModel))
            .timeout(1000 * 60 * 2)
            .timeoutRead(1000 * 60 * 2)
            .awaitString()
        currentModel = newModel
        return newModel
    }

    suspend fun generate(request: Txt2ImgRequest): List<ByteArray> {
        val result = Fuel
            .post("${Config.apiBasePath}/sdapi/v1/txt2img")
            .timeout(1000 * 60 * 2)
            .timeoutRead(1000 * 60 * 2)
            .objectBody(request)
            .await<Txt2ImgResponse>()
        return result.images.map { it.decodeBase64()!! }
    }

    suspend fun generate(
        request: Txt2ImgRequest,
        progressDelayInMs: Long,
        onProgress: (Progress) -> Unit,
    ): List<ByteArray> {
        return coroutineScope {
            val previewJob = launch {
                while (true) {
                    delay(progressDelayInMs)
                    onProgress(getProgress())
                }
            }
            val images = generate(request)
            previewJob.cancel()
            images
        }
    }

    suspend fun getProgress(): Progress {
        val response = Fuel
            .get("${Config.apiBasePath}/sdapi/v1/progress")
            .timeout(1000 * 10)
            .timeoutRead(1000 * 10)
            .objectBody(mapOf("skip_current_image" to false))
            .await<ProgressResponse>()
        return Progress(
            response.progress,
            response.eta,
            // TODO: split preview grid into individual subimages before reporting progress
            response.currentImage?.decodeBase64()?.let { listOf(it) },
        )
    }

    suspend fun inpaint(prompt: String): List<ByteArray> {
        val inputPath = """E:\dev\art\artpieces\dysmorphia\001042_000027_2.png"""
        val inputBytes = File(inputPath).readBytes()
        val img = ImageIO.read(File(inputPath))
        val w = img.width
        val h = img.height
        val inputBase64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(inputBytes)
        val mask = BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY)
        val g = mask.createGraphics()
        g.color = Color.BLACK
        g.fillRect(0, 0, w, h)
//        g.color = Color(128,128,128) // half masking just overlays the generated and the original image over each other. useless!
        g.color = Color.WHITE
        g.fillRect(0, 0, 400, 300)
        val maskBytes = ByteArrayOutputStream().use {
            ImageIO.write(mask, "png", it)
            it.toByteArray()
        }
        val maskBase64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(maskBytes)
        val request = Img2ImgRequest(
            initImages = listOf(inputBase64),
            mask = maskBase64,
            prompt = prompt,
//            prompt = "romantic close up body horror portrait painting painted by sophie gengembre anderson and [beksinski:junji ito:15]. horror, violence, (romance, love, emotional:1.2), ultra detail, dark, fear, (decay, gore:1.1), vibrant glowing eyes, soft lighting, (beautiful:1.6), (flowers:1.1)",
            negativePrompt = "male, hearts, duplicate, multiple heads, watermark, username, (blurry:1.4)",
            samplerIndex = "DPM2 a Karras",
            width = w,
            height = h,
            seed = "123456",
            denoisingStrength = 0.99,
            maskBlur = 4.0,
//            enableHr = true,
        )
        val result = Fuel
            .post("${Config.apiBasePath}/sdapi/v1/img2img")
            .timeout(1000 * 60 * 2)
            .timeoutRead(1000 * 60 * 2)
            .objectBody(request)
            .await<Txt2ImgResponse>()
        return result.images.map { it.decodeBase64()!! }
    }
}

suspend fun main() {
    val out = Api.inpaint(
        "beautiful flowers"
    )
//    for ((i, bytes) in out.withIndex()) {
//        File("""E:\dev\art\artpieces\__unfinished\dysmorphia\out_$i.png""")
//            .writeBytes(bytes)
//    }
}

private suspend inline fun <reified T : Any> Request.await() = await(jacksonDeserializerOf<T>())

private suspend inline fun Request.awaitString() = await(RawDeserializer)

private object RawDeserializer : ResponseDeserializable<String> {
    override fun deserialize(reader: Reader) = reader.readText()
    override fun deserialize(content: String) = content
    override fun deserialize(bytes: ByteArray) = bytes.decodeToString()
    override fun deserialize(inputStream: InputStream) = inputStream.reader().readText()
}

private data class ProgressResponse(
    val progress: Float,
    @JsonProperty("eta_relative") val eta: Float,
    @JsonProperty("current_image") val currentImage: String?,
)

class Progress(
    val progress: Float,
    val eta: Float,
    val currentImages: List<ByteArray>?,
)

data class Txt2ImgRequest(
    val prompt: String,
    @JsonProperty("negative_prompt") val negativePrompt: String,
    @JsonProperty("sampler_index") val sampler: String,
    val width: Int,
    val height: Int,
    val steps: Int,
    @JsonProperty("batch_size") val batchSize: Int,
    @JsonProperty("cfg_scale") val cfgScale: Float,
    val tiling: Boolean = false,
)

private data class Txt2ImgResponse(
    val images: List<String>,
)

data class Img2ImgRequest(
    @JsonProperty("prompt") val prompt: String,
    @JsonProperty("negative_prompt") val negativePrompt: String = "",
    @JsonProperty("seed") val seed: String = "-1",
    @JsonProperty("cfg_scale") val cfgScale: Int = 7,
    @JsonProperty("sampler_index") val samplerIndex: String = "DPM2 a Karras",
    @JsonProperty("steps") val steps: Int = 30,
    @JsonProperty("denoising_strength") val denoisingStrength: Double = 0.3,
    @JsonProperty("mask_blur") val maskBlur: Double = 0.0,
    @JsonProperty("batch_size") val batchSize: Int = 4,
    @JsonProperty("width") val width: Int = 512,
    @JsonProperty("height") val height: Int = 512,
    @JsonProperty("n_iter") val nIter: Int = 1,
    @JsonProperty("mask") val mask: String, // "data:image/png;base64,..."
    @JsonProperty("init_images") val initImages: List<String>, //[ "data:image/png;base64,..." ] -> only ONE image
    @JsonProperty("inpaint_full_res") val inpaintFullRes: Boolean = true,
    @JsonProperty("inpainting_fill") val inpaintingFill: Int = 1,
    @JsonProperty("outpainting_fill") val outpaintingFill: Int = 2,
    @JsonProperty("enable_hr") val enableHr: Boolean = false,
    @JsonProperty("restore_faces") val restoreFaces: Boolean = false,
    @JsonProperty("hr_scale") val hrScale: Int = 2,
    @JsonProperty("hr_upscaler") val hrUpscaler: String = "None",
    @JsonProperty("hr_second_pass_steps") val hrSecondPassSteps: Int = 0,
    @JsonProperty("hr_resize_x") val hrResizeX: Int = 0,
    @JsonProperty("hr_resize_y") val hrResizeY: Int = 0,
    @JsonProperty("hr_square_aspect") val hrSquareAspect: Boolean = false,
    @JsonProperty("styles") val styles: List<String> = emptyList(),
    @JsonProperty("upscale_x") val upscaleX: Int = 1,
    @JsonProperty("hr_denoising_strength") val hrDenoisingStrength: Double = 0.7,
    @JsonProperty("hr_fix_lock_px") val hrFixLockPx: Int = 0,
    @JsonProperty("image_cfg_scale") val imageCfgScale: Int = 30,
    @JsonProperty("alwayson_scripts") val alwaysonScripts: Any = EmptyParamObject,
)

private object EmptyParamObject
