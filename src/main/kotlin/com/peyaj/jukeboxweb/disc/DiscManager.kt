package com.peyaj.jukeboxweb.disc

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.peyaj.jukeboxweb.PeyajCustomDisc
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.io.File

class DiscManager(private val plugin: PeyajCustomDisc) {

    private val discsFile = File(plugin.dataFolder, "discs.json")
    private val mapper = jacksonObjectMapper()
    private val discs = mutableMapOf<String, CustomDisc>()
    
    val namespaceKey = NamespacedKey(plugin, "custom_disc_id")

    fun loadDiscs() {
        if (!discsFile.exists()) {
            discsFile.parentFile.mkdirs()
            mapper.writeValue(discsFile, emptyList<CustomDisc>())
            return
        }

        try {
            val loaded: List<CustomDisc> = mapper.readValue(discsFile)
            discs.clear()
            loaded.forEach { discs[it.id] = it }
            plugin.logger.info("Loaded ${discs.size} custom discs.")
        } catch (e: Exception) {
            plugin.logger.severe("Failed to load discs.json: ${e.message}")
            e.printStackTrace()
        }
    }

    fun saveDiscs() {
        try {
            mapper.writeValue(discsFile, discs.values.toList())
        } catch (e: Exception) {
            plugin.logger.severe("Failed to save discs.json: ${e.message}")
        }
    }

    fun addDisc(disc: CustomDisc) {
        discs[disc.id] = disc
        saveDiscs()
    }
    
    fun deleteDisc(id: String): Boolean {
        if (!discs.containsKey(id)) return false
        discs.remove(id)
        saveDiscs()
        
        // Also delete audio files
        val mp3 = File(plugin.dataFolder, "discs/$id.mp3")
        if(mp3.exists()) mp3.delete()
        val ogg = File(plugin.dataFolder, "discs/$id.ogg")
        if(ogg.exists()) ogg.delete()
        
        return true
    }
    
    fun getDisc(id: String): CustomDisc? = discs[id]
    
    fun getAllDiscs(): List<CustomDisc> = discs.values.toList()

    fun createDiscItem(discId: String): ItemStack? {
        val disc = discs[discId] ?: return null
        
        // Select material based on disc style
        val material = getDiscMaterial(disc.style)
        val item = ItemStack(material)
        val meta = item.itemMeta
        
        meta.displayName(Component.text(disc.name).color(NamedTextColor.AQUA))
        
        val loreLines = mutableListOf<Component>()
        loreLines.add(Component.text(disc.author).color(NamedTextColor.GRAY))
        disc.lore.forEach { line ->
            loreLines.add(Component.text(line).color(NamedTextColor.DARK_GRAY))
        }
        meta.lore(loreLines)
        
        // Custom Model Data for resource pack textures
        if (disc.customModelData != 0) {
            meta.setCustomModelData(disc.customModelData)
        }
        
        // Persistent Data Container to identify it as OUR disc
        meta.persistentDataContainer.set(namespaceKey, PersistentDataType.STRING, disc.id)
        
        // Hide attributes to make it look clean
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
        
        item.itemMeta = meta
        return item
    }
    
    private fun getDiscMaterial(style: String): Material {
        return when(style.lowercase()) {
            "13" -> Material.MUSIC_DISC_13
            "cat" -> Material.MUSIC_DISC_CAT
            "blocks" -> Material.MUSIC_DISC_BLOCKS
            "chirp" -> Material.MUSIC_DISC_CHIRP
            "far" -> Material.MUSIC_DISC_FAR
            "mall" -> Material.MUSIC_DISC_MALL
            "mellohi" -> Material.MUSIC_DISC_MELLOHI
            "stal" -> Material.MUSIC_DISC_STAL
            "strad" -> Material.MUSIC_DISC_STRAD
            "ward" -> Material.MUSIC_DISC_WARD
            "11" -> Material.MUSIC_DISC_11
            "wait" -> Material.MUSIC_DISC_WAIT
            "otherside" -> Material.MUSIC_DISC_OTHERSIDE
            "5" -> Material.MUSIC_DISC_5
            "pigstep" -> Material.MUSIC_DISC_PIGSTEP
            "relic" -> Material.MUSIC_DISC_RELIC
            "creator" -> Material.MUSIC_DISC_CREATOR
            "precipice" -> Material.MUSIC_DISC_PRECIPICE
            "creator_music_box" -> Material.MUSIC_DISC_CREATOR_MUSIC_BOX
            else -> Material.MUSIC_DISC_CAT // Default fallback
        }
    }

    fun isCustomDisc(item: ItemStack?): Boolean {
        if (item == null || !item.hasItemMeta()) return false
        return item.itemMeta.persistentDataContainer.has(namespaceKey, PersistentDataType.STRING)
    }
    
    fun getDiscIdFromItem(item: ItemStack?): String? {
        if (item == null || !item.hasItemMeta()) return null
        return item.itemMeta.persistentDataContainer.get(namespaceKey, PersistentDataType.STRING)
    }
}
