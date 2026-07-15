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

    /**
     * Rebuilds the resource pack from known discs.
     */
    fun buildResourcePack(discs: List<CustomDisc>) {
        // --- Java Pack ---
        // Setup directory structure
        val assetsDir = File(packFolder, "assets/peyajcustomdisc/sounds/disc")
        if (assetsDir.exists()) assetsDir.deleteRecursively()
        assetsDir.mkdirs()

        // Create pack.mcmeta
        val mcmeta = File(packFolder, "pack.mcmeta")
        mcmeta.writeText("""
            {
              "pack": {
                "pack_format": 34,
                "description": "peyajCustomDisc Custom Music"
              }
            }
        """.trimIndent())

        // Create sounds.json structure
        val soundsMap = mutableMapOf<String, Any>()

        // --- Bedrock Pack Setup ---
        if (bedrockFolder.exists()) bedrockFolder.deleteRecursively()
        bedrockFolder.mkdirs()
        
        val bedrockSoundsDir = File(bedrockFolder, "sounds/peyajcustomdisc")
        bedrockSoundsDir.mkdirs()

        val soundDefinitions = mutableMapOf<String, Any>()

        for (disc in discs) {
            // ... (File finding logic same as before)
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
                // Java Pack Audio
                val targetFile = File(assetsDir, "${disc.id}.$ext")
                sourceFile.copyTo(targetFile, overwrite = true)
                
                // Bedrock Pack Copy
                // Bedrock prefers OGG mostly. If valid MP3 or OGG it usually plays.
                val bedrockTarget = File(bedrockSoundsDir, "${disc.id}.$ext")
                sourceFile.copyTo(bedrockTarget, overwrite = true)

                // Java sounds.json
                val soundEntry = mapOf(
                    "category" to "record",
                    "sounds" to listOf(
                        mapOf(
                            "name" to "peyajcustomdisc:disc/${disc.id}",
                            "stream" to true,
                            "attenuation_distance" to 64
                        )
                    )
                )
                soundsMap["disc.${disc.id}"] = soundEntry

                // Bedrock sound_definitions.json
                // Key: peyajcustomdisc:disc.id
                val bedrockEntry = mapOf(
                    "category" to "record",
                    "sounds" to listOf(
                        mapOf(
                            "name" to "sounds/peyajcustomdisc/${disc.id}",
                            "volume" to 1.0,
                            "pitch" to 1.0,
                            "load_on_low_memory" to true,
                            "stream" to true
                        )
                    ),
                    "max_distance" to 64,
                    "min_distance" to 4
                )
                soundDefinitions["peyajcustomdisc:disc.${disc.id}"] = bedrockEntry
            } 
        }

        // --- Finalize Java Pack ---
        val namespaceDir = File(packFolder, "assets/peyajcustomdisc")
        if (!namespaceDir.exists()) namespaceDir.mkdirs()
        
        val soundsJsonFile = File(namespaceDir, "sounds.json")
        mapper.writerWithDefaultPrettyPrinter().writeValue(soundsJsonFile, soundsMap)
        
        zipFolder(packFolder, zipFile)
        plugin.logger.info("Java Resource pack generated at ${zipFile.absolutePath}")

        // --- Finalize Bedrock Pack ---
        // 1. manifest.json
        val manifest = mapOf(
            "format_version" to 2,
            "header" to mapOf(
                "description" to "peyajCustomDisc Geyser Music",
                "name" to "peyajCustomDisc Pack",
                "uuid" to java.util.UUID.randomUUID().toString(),
                "version" to listOf(1, 0, 0),
                "min_engine_version" to listOf(1, 16, 0)
            ),
            "modules" to listOf(
                mapOf(
                    "description" to "Custom Discs",
                    "type" to "resources",
                    "uuid" to java.util.UUID.randomUUID().toString(),
                    "version" to listOf(1, 0, 0)
                )
            )
        )
        val manifestFile = File(bedrockFolder, "manifest.json")
        mapper.writerWithDefaultPrettyPrinter().writeValue(manifestFile, manifest)

        // 2. sound_definitions.json
        val soundDefFile = File(bedrockFolder, "sounds/sound_definitions.json")
        mapper.writerWithDefaultPrettyPrinter().writeValue(soundDefFile, mapOf(
            "format_version" to "1.14.0",
            "sound_definitions" to soundDefinitions
        ))

        zipFolder(bedrockFolder, bedrockPackFile)
        plugin.logger.info("Bedrock Resource pack generated at ${bedrockPackFile.absolutePath}")
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
}
