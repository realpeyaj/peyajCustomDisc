package com.peyaj.jukeboxweb.disc

import com.peyaj.jukeboxweb.PeyajCustomDisc
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class PaperDiscManager(private val plugin: PeyajCustomDisc) : DiscManager(
    dataFolder = plugin.dataFolder,
    logInfo = { plugin.logger.info(it) },
    logSevere = { msg, err -> plugin.logger.severe(msg); err?.printStackTrace() }
) {
    val namespaceKey = NamespacedKey(plugin, "custom_disc_id")

    fun createDiscItem(discId: String): ItemStack? {
        val disc = discs[discId] ?: return null
        
        val item = ItemStack(Material.PAPER)
        val meta = item.itemMeta
        
        meta.displayName(Component.text("♫ ${disc.name} ♫").color(NamedTextColor.AQUA))
        
        val loreLines = mutableListOf<Component>()
        loreLines.add(Component.text("Artist: ${disc.author}", NamedTextColor.YELLOW))
        
        if (disc.durationSeconds > 0) {
            val mins = disc.durationSeconds / 60
            val secs = disc.durationSeconds % 60
            val durStr = String.format("%d:%02d", mins, secs)
            loreLines.add(Component.text("Duration: $durStr", NamedTextColor.GRAY))
        }
        
        if (disc.lore.isNotEmpty()) {
            loreLines.add(Component.empty())
            disc.lore.forEach { line ->
                loreLines.add(Component.text(line, NamedTextColor.DARK_AQUA))
            }
        }
        meta.lore(loreLines)
        
        val discCleanId = disc.id.lowercase().replace(Regex("[^a-z0-9_]"), "_")
        val modelData = if (disc.customModelData != 0) disc.customModelData else (10000 + Math.abs(discCleanId.hashCode()) % 50000)
        meta.setCustomModelData(modelData)
        
        meta.persistentDataContainer.set(namespaceKey, PersistentDataType.STRING, disc.id)
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
        
        item.itemMeta = meta
        return item
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
