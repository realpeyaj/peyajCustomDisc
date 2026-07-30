package com.peyaj.jukeboxweb.pack

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.peyaj.jukeboxweb.PeyajCustomDisc
import com.peyaj.jukeboxweb.disc.CustomDisc
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PackGenerator(private val plugin: PeyajCustomDisc) {

    private val packFolder = File(plugin.dataFolder, "pack")
    private val zipFile = File(plugin.dataFolder, "peyajCD-Java.zip")
    private val mapper = jacksonObjectMapper()
    private val bedrockPackFile = File(plugin.dataFolder, "peyajCD-Bedrock.mcpack")
    private val bedrockFolder = File(plugin.dataFolder, "bedrock_pack")

    init {
        if (!packFolder.exists()) packFolder.mkdirs()
        if (!bedrockFolder.exists()) bedrockFolder.mkdirs()
    }

    fun buildResourcePack(discs: List<CustomDisc>) {
        val packMetaFile = File(plugin.dataFolder, "pack_meta.json")
        val currentDiscHash = discs.map { "${it.id}:${it.name}:${it.style}" }.sorted().joinToString("|")
        
        if (packMetaFile.exists() && zipFile.exists() && bedrockPackFile.exists()) {
            val existingMeta = try { mapper.readValue(packMetaFile, Map::class.java) } catch (e: Exception) { null }
            if (existingMeta != null && existingMeta["discHash"] == currentDiscHash) {
                plugin.logger.info("Discs haven't changed. Skipping pack generation to preserve hashes!")
                return
            }
        }
        
        if (packFolder.exists()) packFolder.deleteRecursively()
        packFolder.mkdirs()

        if (bedrockFolder.exists()) bedrockFolder.deleteRecursively()
        bedrockFolder.mkdirs()

        val assetsDir = File(packFolder, "assets/peyajcustomdisc/sounds/disc")
        assetsDir.mkdirs()

        val mcTexturesDir = File(packFolder, "assets/minecraft/textures/item")
        mcTexturesDir.mkdirs()

        val mcModelsDir = File(packFolder, "assets/minecraft/models/item")
        mcModelsDir.mkdirs()

        val mcItemsDir = File(packFolder, "assets/minecraft/items")
        mcItemsDir.mkdirs()

        val peyajTexturesDir = File(packFolder, "assets/peyajcustomdisc/textures/item/disc")
        peyajTexturesDir.mkdirs()

        val peyajModelsDir = File(packFolder, "assets/peyajcustomdisc/models/item/disc")
        peyajModelsDir.mkdirs()

        val mcmeta = File(packFolder, "pack.mcmeta")
        mcmeta.writeText("""
            {
              "pack": {
                "pack_format": 34,
                "supported_formats": [15, 60],
                "description": "peyajCustomDisc Custom Music"
              }
            }
        """.trimIndent())

        val bedrockSoundsDir = File(bedrockFolder, "sounds/peyajcustomdisc")
        bedrockSoundsDir.mkdirs()

        val bedrockTexturesDir = File(bedrockFolder, "textures/items/disc")
        bedrockTexturesDir.mkdirs()

        val soundDefinitions = mutableMapOf<String, Any>()
        val itemTextureData = mutableMapOf<String, Any>()
        val soundsMap = mutableMapOf<String, Any>()
        val overridesMap = mutableMapOf<String, MutableList<Map<String, Any>>>()

        for (disc in discs) {
            val discCleanId = disc.id.lowercase().replace(Regex("[^a-z0-9_]"), "_")
            val sourceFileMp3 = File(plugin.dataFolder, "discs/${disc.id}.mp3")
            val sourceFileOgg = File(plugin.dataFolder, "discs/${disc.id}.ogg")
            
            var sourceFile: File? = null
            var ext = "ogg"
            
            if (sourceFileOgg.exists()) {
                sourceFile = sourceFileOgg
                ext = "ogg"
            } else if (sourceFileMp3.exists()) {
                sourceFile = sourceFileMp3
                ext = "mp3"
            }

            if (sourceFile != null) {
                val targetFile = File(assetsDir, "$discCleanId.$ext")
                sourceFile.copyTo(targetFile, overwrite = true)
                
                var bedrockOggFile = sourceFileOgg
                if (!bedrockOggFile.exists() && sourceFileMp3.exists()) {
                    if (com.peyaj.jukeboxweb.util.AudioConverter.isFFmpegAvailable()) {
                        val (success, _) = com.peyaj.jukeboxweb.util.AudioConverter.convertToOgg(sourceFileMp3, sourceFileOgg)
                        if (success) {
                            bedrockOggFile = sourceFileOgg
                        }
                    }
                }

                if (bedrockOggFile.exists()) {
                    val bedrockTarget = File(bedrockSoundsDir, "$discCleanId.ogg")
                    bedrockOggFile.copyTo(bedrockTarget, overwrite = true)
                }

                soundsMap["disc.$discCleanId"] = mapOf(
                    "category" to "record",
                    "sounds" to listOf(
                        mapOf(
                            "name" to "peyajcustomdisc:disc/$discCleanId",
                            "stream" to true,
                            "attenuation_distance" to 64
                        )
                    )
                )

                val bedrockSoundEntry = mapOf(
                    "category" to "record",
                    "sounds" to listOf(
                        mapOf("name" to "sounds/peyajcustomdisc/$discCleanId", "volume" to 1.0, "pitch" to 1.0, "stream" to true)
                    ),
                    "max_distance" to 64,
                    "min_distance" to 4
                )

                soundDefinitions["peyajcustomdisc.disc.$discCleanId"] = bedrockSoundEntry
                soundDefinitions["peyajcustomdisc:disc.$discCleanId"] = bedrockSoundEntry
                soundDefinitions["disc.$discCleanId"] = bedrockSoundEntry
                soundDefinitions[discCleanId] = bedrockSoundEntry
            }

            val textureFile = File(plugin.dataFolder, "discs/${disc.id}.png")
            val cmd = if (disc.customModelData != 0) disc.customModelData else (10000 + Math.abs(discCleanId.hashCode()) % 50000)

            val mcTextureTarget = File(mcTexturesDir, "disc_$discCleanId.png")
            val peyajTextureTarget = File(peyajTexturesDir, "$discCleanId.png")
            val bedrockTextureTarget = File(bedrockTexturesDir, "$discCleanId.png")

            if (textureFile.exists()) {
                textureFile.copyTo(mcTextureTarget, overwrite = true)
                textureFile.copyTo(peyajTextureTarget, overwrite = true)
                textureFile.copyTo(bedrockTextureTarget, overwrite = true)
            } else {
                generateDefaultDiscImage(mcTextureTarget, discCleanId)
                generateDefaultDiscImage(peyajTextureTarget, discCleanId)
                generateDefaultDiscImage(bedrockTextureTarget, discCleanId)
            }

            itemTextureData["music_disc_$discCleanId"] = mapOf(
                "textures" to "textures/items/disc/$discCleanId"
            )

            val mcModelFile = File(mcModelsDir, "disc_$discCleanId.json")
            val mcModelContent = mapOf(
                "parent" to "minecraft:item/generated",
                "textures" to mapOf(
                    "layer0" to "peyajcustomdisc:item/disc/$discCleanId"
                )
            )
            mapper.writerWithDefaultPrettyPrinter().writeValue(mcModelFile, mcModelContent)

            val peyajModelFile = File(peyajModelsDir, "$discCleanId.json")
            val peyajModelContent = mapOf(
                "parent" to "minecraft:item/generated",
                "textures" to mapOf(
                    "layer0" to "peyajcustomdisc:item/disc/$discCleanId"
                )
            )
            mapper.writerWithDefaultPrettyPrinter().writeValue(peyajModelFile, peyajModelContent)

            val overrideEntry = mapOf(
                "predicate" to mapOf(
                    "custom_model_data" to cmd
                ),
                "model" to "peyajcustomdisc:item/disc/$discCleanId"
            )

            val stylesToOverride = listOf(
                "music_disc_11", "paper", disc.style.lowercase(),
                "music_disc_${disc.style.lowercase().replace("music_disc_", "")}"
            )
            stylesToOverride.distinct().forEach { style ->
                if (style.isNotBlank()) {
                    overridesMap.computeIfAbsent(style) { mutableListOf() }.add(overrideEntry)
                }
            }
        }

        overridesMap.forEach { (styleName, overrides) ->
            try {
                overrides.sortBy { (it["predicate"] as Map<*, *>)["custom_model_data"] as Int }
                
                // Legacy models/item/*.json (pre-1.21.2 clients)
                val modelFile = File(mcModelsDir, "$styleName.json")
                val modelData = mapOf(
                    "parent" to "minecraft:item/generated",
                    "textures" to mapOf(
                        "layer0" to "minecraft:item/$styleName"
                    ),
                    "overrides" to overrides
                )
                mapper.writerWithDefaultPrettyPrinter().writeValue(modelFile, modelData)

                // Modern items/*.json (1.21.4+ range_dispatch format)
                val itemFile = File(mcItemsDir, "$styleName.json")
                val entriesList = overrides.map { override ->
                    val cmdVal = (override["predicate"] as Map<*, *>)["custom_model_data"] as Int
                    val modelPath = override["model"] as String
                    mapOf(
                        "threshold" to cmdVal,
                        "model" to mapOf(
                            "type" to "minecraft:model",
                            "model" to modelPath
                        )
                    )
                }
                val itemData = mapOf(
                    "model" to mapOf(
                        "type" to "minecraft:range_dispatch",
                        "property" to "minecraft:custom_model_data",
                        "entries" to entriesList,
                        "fallback" to mapOf(
                            "type" to "minecraft:model",
                            "model" to "minecraft:item/$styleName"
                        )
                    )
                )
                mapper.writerWithDefaultPrettyPrinter().writeValue(itemFile, itemData)
            } catch (e: Exception) {
                plugin.logger.severe("Failed to write model override for $styleName: ${e.message}")
            }
        }

        val namespaceDir = File(packFolder, "assets/peyajcustomdisc")
        if (!namespaceDir.exists()) namespaceDir.mkdirs()
        val soundsJsonFile = File(namespaceDir, "sounds.json")
        mapper.writerWithDefaultPrettyPrinter().writeValue(soundsJsonFile, soundsMap)
        
        zipFolder(packFolder, zipFile)
        plugin.logger.info("✔ Java Resource pack generated at ${zipFile.absolutePath}")

        // Build Geyser Custom Mappings for Bedrock item textures
        val customMappingsDir = File(bedrockFolder, "custom_mappings")
        customMappingsDir.mkdirs()
        val geyserItemsMap = mutableMapOf<String, MutableList<Map<String, Any>>>()

        for (disc in discs) {
            val discCleanId = disc.id.lowercase().replace(Regex("[^a-z0-9_]"), "_")
            val cmd = if (disc.customModelData != 0) disc.customModelData else (10000 + Math.abs(discCleanId.hashCode()) % 50000)
            
            val entry = mapOf(
                "custom_model_data" to cmd,
                "name" to "peyaj_music_disc_$discCleanId",
                "icon" to "music_disc_$discCleanId"
            )
            geyserItemsMap.computeIfAbsent("minecraft:paper") { mutableListOf() }.add(entry)
        }

        val geyserMappingsFile = File(customMappingsDir, "peyaj_mappings.json")
        mapper.writerWithDefaultPrettyPrinter().writeValue(geyserMappingsFile, mapOf(
            "format_version" to 1,
            "items" to geyserItemsMap
        ))

        val bedrockMetaFile = File(plugin.dataFolder, "bedrock_meta.json")
        var headerUuid = java.util.UUID.randomUUID().toString()
        var moduleUuid = java.util.UUID.randomUUID().toString()
        var patchVersion = 0
        
        if (bedrockMetaFile.exists()) {
            try {
                val existingMeta = mapper.readValue(bedrockMetaFile, Map::class.java)
                val previousHash = existingMeta["discHash"] as? String ?: ""
                val lastPatch = (existingMeta["patchVersion"] as? Number)?.toInt() ?: 0
                patchVersion = lastPatch + 1

                if (previousHash == currentDiscHash && previousHash.isNotEmpty()) {
                    headerUuid = existingMeta["headerUuid"] as? String ?: headerUuid
                    moduleUuid = existingMeta["moduleUuid"] as? String ?: moduleUuid
                } else {
                    headerUuid = java.util.UUID.randomUUID().toString()
                    moduleUuid = java.util.UUID.randomUUID().toString()
                }
            } catch (e: Exception) {
                plugin.logger.warning("Resetting Bedrock metadata.")
            }
        }

        val updatedMeta = mapOf(
            "headerUuid" to headerUuid,
            "moduleUuid" to moduleUuid,
            "patchVersion" to patchVersion,
            "discHash" to currentDiscHash
        )
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(bedrockMetaFile, updatedMeta)
        } catch (e: Exception) {}

        val packVersion = listOf(1, 0, patchVersion)

        val manifest = mapOf(
            "format_version" to 2,
            "header" to mapOf(
                "description" to "peyajCustomDisc Geyser Music",
                "name" to "peyajCustomDisc Pack",
                "uuid" to headerUuid,
                "version" to packVersion,
                "min_engine_version" to listOf(1, 16, 0)
            ),
            "modules" to listOf(
                mapOf(
                    "description" to "Custom Discs",
                    "type" to "resources",
                    "uuid" to moduleUuid,
                    "version" to packVersion
                )
            )
        )
        val manifestFile = File(bedrockFolder, "manifest.json")
        mapper.writerWithDefaultPrettyPrinter().writeValue(manifestFile, manifest)

        val itemTextureFile = File(bedrockFolder, "textures/item_texture.json")
        mapper.writerWithDefaultPrettyPrinter().writeValue(itemTextureFile, mapOf(
            "resource_pack_name" to "peyajCustomDisc",
            "texture_name" to "atlas.items",
            "texture_data" to itemTextureData
        ))

        val soundDefFile = File(bedrockFolder, "sounds/sound_definitions.json")
        mapper.writerWithDefaultPrettyPrinter().writeValue(soundDefFile, mapOf(
            "format_version" to "1.14.0",
            "sound_definitions" to soundDefinitions
        ))

        zipFolder(bedrockFolder, bedrockPackFile)
        plugin.logger.info("✔ Bedrock Resource pack generated at ${bedrockPackFile.absolutePath}")

        val geyserDirs = listOf(
            File(plugin.dataFolder.parentFile, "Geyser-Spigot"),
            File(plugin.dataFolder.parentFile, "Geyser-Paper"),
            File(plugin.dataFolder.parentFile, "Geyser")
        )
        geyserDirs.forEach { geyserDir ->
            if (geyserDir.exists()) {
                try {
                    val packsDir = File(geyserDir, "packs")
                    packsDir.mkdirs()
                    bedrockPackFile.copyTo(File(packsDir, "peyajCD-Bedrock.mcpack"), overwrite = true)
                    plugin.logger.info("✔ Bedrock pack auto-copied to ${packsDir.path}/peyajCD-Bedrock.mcpack")

                    val mappingsDir = File(geyserDir, "custom_mappings")
                    mappingsDir.mkdirs()
                    geyserMappingsFile.copyTo(File(mappingsDir, "peyaj_mappings.json"), overwrite = true)
                    plugin.logger.info("✔ Geyser custom_mappings auto-copied to ${mappingsDir.path}/peyaj_mappings.json")
                } catch (e: Exception) {
                    plugin.logger.warning("Could not auto-copy Bedrock pack/mappings to ${geyserDir.path}: ${e.message}")
                }
            }
        }
        
        // Save pack metadata to prevent regeneration on next boot if discs haven't changed
        try {
            val packMetaFile = File(plugin.dataFolder, "pack_meta.json")
            val currentDiscHash = discs.map { "${it.id}:${it.name}:${it.style}" }.sorted().joinToString("|")
            mapper.writeValue(packMetaFile, mapOf("discHash" to currentDiscHash))
        } catch (e: Exception) {
            plugin.logger.warning("Failed to save pack_meta.json: ${e.message}")
        }
    }
    
    fun getPackHash(): String {
        if (!zipFile.exists()) return ""
        val digest = MessageDigest.getInstance("SHA-1")
        FileInputStream(zipFile).use { fis ->
            val updateBuffer = ByteArray(1024)
            var read: Int
            while (fis.read(updateBuffer).also { read = it } != -1) {
                digest.update(updateBuffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    
    fun getPackFile(): File = zipFile
    fun getBedrockPackFile(): File = bedrockPackFile

    private fun zipFolder(sourceFolder: File, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            sourceFolder.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val entryName = file.relativeTo(sourceFolder).path.replace("\\", "/")
                    zos.putNextEntry(ZipEntry(entryName))
                    FileInputStream(file).use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
        }
    }

    private fun generateDefaultDiscImage(targetFile: File, discId: String) {
        try {
            val img = java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            
            val hash = Math.abs(discId.hashCode())
            val hue = (hash % 360) / 360.0f
            val discColor = java.awt.Color.getHSBColor(hue, 0.8f, 0.9f)
            val centerColor = java.awt.Color.getHSBColor((hue + 0.5f) % 1.0f, 0.9f, 1.0f)
            val darkRim = java.awt.Color(20, 20, 20)

            g.color = darkRim
            g.fillOval(1, 1, 14, 14)

            g.color = discColor
            g.fillOval(3, 3, 10, 10)

            g.color = centerColor
            g.fillOval(6, 6, 4, 4)

            g.color = java.awt.Color(0, 0, 0, 0)
            g.composite = java.awt.AlphaComposite.Clear
            g.fillOval(7, 7, 2, 2)

            g.dispose()
            javax.imageio.ImageIO.write(img, "PNG", targetFile)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to generate procedural disc image for $discId: ${e.message}")
        }
    }
}
