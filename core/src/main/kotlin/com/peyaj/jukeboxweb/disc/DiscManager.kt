package com.peyaj.jukeboxweb.disc

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.util.concurrent.ConcurrentHashMap

open class DiscManager(
    val dataFolder: File,
    private val logInfo: (String) -> Unit = {},
    private val logSevere: (String, Throwable?) -> Unit = { _, _ -> }
) {
    private val discsFile = File(dataFolder, "discs.json")
    private val mapper = jacksonObjectMapper()
    protected val discs = ConcurrentHashMap<String, CustomDisc>()

    fun loadDiscs() {
        if (!discsFile.exists()) {
            discsFile.parentFile.mkdirs()
            mapper.writeValue(discsFile, emptyList<CustomDisc>())
            return
        }

        try {
            val loaded: List<CustomDisc> = mapper.readValue(discsFile)
            discs.clear()
            var modified = false
            var currentMaxCmd = loaded.maxOfOrNull { it.customModelData } ?: 10000
            if (currentMaxCmd < 10000) currentMaxCmd = 10000

            loaded.forEach { d ->
                var updated = d
                if (updated.customModelData == 0) {
                    currentMaxCmd++
                    updated = updated.copy(customModelData = currentMaxCmd)
                    modified = true
                }
                if (updated.durationSeconds <= 0) {
                    val oggFile = File(dataFolder, "discs/${updated.id}.ogg")
                    val mp3File = File(dataFolder, "discs/${updated.id}.mp3")
                    val fileToProbe = if (oggFile.exists()) oggFile else if (mp3File.exists()) mp3File else null
                    if (fileToProbe != null) {
                        val detected = com.peyaj.jukeboxweb.util.AudioConverter.getAudioDuration(fileToProbe)
                        if (detected > 0) {
                            updated = updated.copy(durationSeconds = detected)
                            modified = true
                            logInfo("Auto-detected duration for '${updated.id}': ${detected}s")
                        }
                    }
                }
                discs[updated.id] = updated
            }
            if (modified) {
                saveDiscs()
            }
            logInfo("Loaded ${discs.size} custom discs.")
        } catch (e: Exception) {
            logSevere("Failed to load discs.json: ${e.message}", e)
        }
    }

    fun saveDiscs() {
        try {
            mapper.writeValue(discsFile, discs.values.toList())
        } catch (e: Exception) {
            logSevere("Failed to save discs.json: ${e.message}", e)
        }
    }

    fun getNextCustomModelData(): Int {
        val maxCmd = discs.values.maxOfOrNull { it.customModelData } ?: 10000
        return maxOf(maxCmd + 1, 10001)
    }

    fun addDisc(disc: CustomDisc) {
        val finalDisc = if (disc.customModelData == 0) {
            disc.copy(customModelData = getNextCustomModelData())
        } else {
            disc
        }
        discs[finalDisc.id] = finalDisc
        saveDiscs()
    }

    fun deleteDisc(id: String): Boolean {
        if (!discs.containsKey(id)) return false
        discs.remove(id)
        saveDiscs()

        val mp3 = File(dataFolder, "discs/$id.mp3")
        if (mp3.exists()) mp3.delete()
        val ogg = File(dataFolder, "discs/$id.ogg")
        if (ogg.exists()) ogg.delete()
        val stereoOgg = File(dataFolder, "discs/${id}_stereo.ogg")
        if (stereoOgg.exists()) stereoOgg.delete()
        val png = File(dataFolder, "discs/$id.png")
        if (png.exists()) png.delete()

        return true
    }

    fun getDisc(id: String): CustomDisc? = discs[id]

    fun getAllDiscs(): List<CustomDisc> = discs.values.toList()
}
