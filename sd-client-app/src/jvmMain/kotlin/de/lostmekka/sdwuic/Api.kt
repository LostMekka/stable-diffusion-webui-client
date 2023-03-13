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
import java.io.InputStream
import java.io.Reader

object Api {
    private var availableModels: List<String>? = null
    suspend fun getAvailableModels(forceReload: Boolean = false): List<String> {
        val currModels = availableModels
        return if (currModels == null || forceReload) {
            data class Model(val title: String)
            Fuel.get("${Config.apiBasePath}/sdapi/v1/sd-models")
                .await<List<Model>>()
                .map { it.title }
                .also { availableModels = it }
        } else {
            currModels
        }
    }

    private var availableSamplers: List<String>? = null
    suspend fun getAvailableSamplers(forceReload: Boolean = false): List<String> {
        val cache = availableSamplers
        return if (cache == null || forceReload) {
            data class Sampler(val name: String)
            Fuel.get("${Config.apiBasePath}/sdapi/v1/samplers")
                .await<List<Sampler>>()
                .map { it.name }
                .also { availableSamplers = it }
        } else {
            cache
        }
    }

    private var currentModel: String? = null
    suspend fun getCurrentModel(): String {
        return currentModel ?: Fuel
            .get("${Config.apiBasePath}/sdapi/v1/options")
            .await<Map<String, Any>>()
            .let { it["sd_model_checkpoint"] as String }
            .also { currentModel = it }
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

    suspend fun generate(request: Txt2ImgRequest, progressDelayInMs: Long, onProgress: (Progress) -> Unit): List<ByteArray> {
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
}

private suspend inline fun <reified T: Any> Request.await() = await(jacksonDeserializerOf<T>())

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
