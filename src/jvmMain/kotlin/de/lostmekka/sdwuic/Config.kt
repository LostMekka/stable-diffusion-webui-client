package de.lostmekka.sdwuic

import java.io.File
import java.util.Properties

object Config {
    private val propertiesFile = File("config.properties")
    private val properties = Properties()

    init {
        if (propertiesFile.exists()) {
            propertiesFile.inputStream().use { properties.load(it) }
        }
        properties.setDefault("apiBasePath", "http://127.0.0.1:7860")
        properties.setDefault("configIdFilePath", "out/nextConfigId.txt")
        properties.setDefault("stagingDirPath", "out/staging")
        propertiesFile.outputStream().use { properties.store(it, null) }
    }

    private fun Properties.setDefault(key: String, value: String) {
        if (!containsKey(key)) put(key, value)
    }

    val apiBasePath: String by properties
    val configIdFilePath: String by properties
    val stagingDirPath: String by properties
}
