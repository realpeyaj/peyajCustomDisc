package com.peyaj.jukeboxweb.fabric.config

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File

data class FabricConfig(
    var webPort: Int = 8080,
    var publicUrl: String = "http://localhost:8080",
    var adminPassword: String = "changeme",
    var haProxySupport: Boolean = false,
    var autoReloadGeyser: Boolean = true,
    var autoUpdatePack: Boolean = true,
    var jukeboxRange: Double = 64.0,
    var sendOnJoin: Boolean = true,
    var prompt: String = "<gold>Download Custom Discs</gold>",
    var required: Boolean = false
) {
    companion object {
        private val mapper = jacksonObjectMapper()

        fun load(configFile: File): FabricConfig {
            if (!configFile.exists()) {
                configFile.parentFile?.mkdirs()
                val defaultConfig = FabricConfig()
                save(configFile, defaultConfig)
                return defaultConfig
            }
            return try {
                mapper.readValue<FabricConfig>(configFile)
            } catch (e: Exception) {
                FabricConfig()
            }
        }

        fun save(configFile: File, config: FabricConfig) {
            try {
                configFile.parentFile?.mkdirs()
                mapper.writerWithDefaultPrettyPrinter().writeValue(configFile, config)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
