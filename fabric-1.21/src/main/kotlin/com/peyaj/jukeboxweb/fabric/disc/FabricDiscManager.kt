package com.peyaj.jukeboxweb.fabric.disc

import com.peyaj.jukeboxweb.disc.DiscManager
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.CustomModelDataComponent
import net.minecraft.component.type.LoreComponent
import net.minecraft.component.type.NbtComponent
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtCompound
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.io.File

class FabricDiscManager(
    dataFolder: File,
    logInfo: (String) -> Unit = {},
    logSevere: (String, Throwable?) -> Unit = { _, _ -> }
) : DiscManager(dataFolder, logInfo, logSevere) {

    fun createDiscItem(discId: String): ItemStack? {
        val disc = discs[discId] ?: return null
        
        val item = ItemStack(Items.PAPER)
        
        val discCleanId = disc.id.lowercase().replace(Regex("[^a-z0-9_]"), "_")
        val cmd = if (disc.customModelData != 0) disc.customModelData else (10000 + Math.abs(discCleanId.hashCode()) % 50000)
        item.set(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelDataComponent(cmd))
        
        item.set(DataComponentTypes.ITEM_NAME, Text.literal("♫ ${disc.name} ♫").formatted(Formatting.AQUA))
        
        val loreLines = mutableListOf<Text>()
        loreLines.add(Text.literal("Artist: ${disc.author}").formatted(Formatting.YELLOW))
        
        if (disc.durationSeconds > 0) {
            val mins = disc.durationSeconds / 60
            val secs = disc.durationSeconds % 60
            val durStr = String.format("%d:%02d", mins, secs)
            loreLines.add(Text.literal("Duration: $durStr").formatted(Formatting.GRAY))
        }
        
        if (disc.lore.isNotEmpty()) {
            loreLines.add(Text.empty())
            disc.lore.forEach { line ->
                loreLines.add(Text.literal(line).formatted(Formatting.DARK_AQUA))
            }
        }
        item.set(DataComponentTypes.LORE, LoreComponent(loreLines))
        
        val nbt = NbtCompound()
        nbt.putString("custom_disc_id", disc.id)
        item.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt))
        
        return item
    }

    fun isCustomDisc(stack: ItemStack?): Boolean {
        if (stack == null || stack.isEmpty) return false
        val customData = stack.get(DataComponentTypes.CUSTOM_DATA) ?: return false
        return customData.copyNbt().contains("custom_disc_id")
    }

    fun getDiscIdFromItem(stack: ItemStack?): String? {
        if (stack == null || stack.isEmpty) return null
        val customData = stack.get(DataComponentTypes.CUSTOM_DATA) ?: return null
        val nbt = customData.copyNbt()
        return if (nbt.contains("custom_disc_id")) nbt.getString("custom_disc_id") else null
    }
}
