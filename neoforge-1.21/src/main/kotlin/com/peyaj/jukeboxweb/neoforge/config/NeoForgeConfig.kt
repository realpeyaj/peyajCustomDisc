package com.peyaj.jukeboxweb.neoforge.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File

data class NeoForgeConfig(
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
        private val mapper = jacksonObjectMapper().apply {
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }

        fun load(configFile: File): NeoForgeConfig {
            if (!configFile.exists()) {
                configFile.parentFile?.mkdirs()
                val defaultConfig = NeoForgeConfig()
                save(configFile, defaultConfig)
                return defaultConfig
            }
            return try {
                val loaded = mapper.readValue<NeoForgeConfig>(configFile)
                save(configFile, loaded)
                loaded
            } catch (e: Exception) {
                NeoForgeConfig()
            }
        }

        fun save(configFile: File, config: NeoForgeConfig) {
            try {
                configFile.parentFile?.mkdirs()
                mapper.writerWithDefaultPrettyPrinter().writeValue(configFile, config)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
