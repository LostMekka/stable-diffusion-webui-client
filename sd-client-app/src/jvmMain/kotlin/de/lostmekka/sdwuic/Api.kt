package de.lostmekka.sdwuic

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.objectBody
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.fuel.util.decodeBase64

object Api {
    private var fullConfig: FullConfigResponse? = null
    private fun reloadFullConfig(): FullConfigResponse {
        return Fuel
            .get("${Config.apiBasePath}/config")
            .responseObject<FullConfigResponse>()
            .third
            .get()
            .also { fullConfig = it }
    }

    private fun getFullConfig() = synchronized(this) { fullConfig ?: reloadFullConfig() }

    fun getAvailableModels(): List<String> {
        val element = getFullConfig().components.first { it.props.elementId == "setting_sd_model_checkpoint" }
        return element.props.choices.orEmpty()
    }

    fun getAvailableTxt2ImgSamplers(): List<String> {
        val element = getFullConfig().components.first { it.props.elementId == "txt2img_sampling" }
        return element.props.choices.orEmpty()
    }

    fun getAvailableImg2ImgSamplers(): List<String> {
        val element = getFullConfig().components.first { it.props.elementId == "img2img_sampling" }
        return element.props.choices.orEmpty()
    }

    private var currentModel: String? = null
    fun getCurrentModel(): String {
        return currentModel ?: Fuel
            .get("${Config.apiBasePath}/sdapi/v1/options")
            .responseObject<Map<String, Any>>()
            .third
            .get()
            .let { it["sd_model_checkpoint"] as String }
            .also { currentModel = it }
    }

    fun setModel(newModel: String): String {
        require(newModel in getAvailableModels())
        if (newModel == currentModel) return newModel
        Fuel.post("${Config.apiBasePath}/sdapi/v1/options")
            .objectBody(mapOf("sd_model_checkpoint" to newModel))
            .timeout(1000 * 60 * 2)
            .timeoutRead(1000 * 60 * 2)
            .responseString()
            .third
            .get()
        currentModel = newModel
        return newModel
    }

    fun generate(request: Txt2ImgRequest): List<ByteArray> {
        val (_, _, result) = Fuel
            .post("${Config.apiBasePath}/sdapi/v1/txt2img")
            .timeout(1000 * 60 * 2)
            .timeoutRead(1000 * 60 * 2)
            .objectBody(request)
            .responseObject<Txt2ImgResponse>()
        return result.get().images.map { it.decodeBase64()!! }
    }
}

private data class FullConfigResponse(
    val version: String,
    val components: List<ConfigDefinitionElement>,
)

private data class ConfigDefinitionElement(
    val id: Int,
    val type: String,
    val props: ConfigDefinitionElementProps,
)

private data class ConfigDefinitionElementProps(
    val choices: List<String>?,
    val value: Any?,
    @JsonProperty("elem_id") val elementId: String?,
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
)

private data class Txt2ImgResponse(
    val images: List<String>,
)
