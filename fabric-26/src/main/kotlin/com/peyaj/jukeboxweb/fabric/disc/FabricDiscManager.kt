package com.peyaj.jukeboxweb.fabric.disc

import com.peyaj.jukeboxweb.disc.DiscManager
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.CustomModelData
import net.minecraft.world.item.component.ItemLore
import java.io.File

class FabricDiscManager(
    dataFolder: File,
    logInfo: (String) -> Unit = {},
    logSevere: (String, Throwable?) -> Unit = { _, _ -> }
) : DiscManager(dataFolder, logInfo, logSevere) {

    fun createDiscItem(discId: String): ItemStack? {
        val disc = discs[discId] ?: return null
        
        val item = ItemStack(Items.PAPER)
        
        item.set(DataComponents.CUSTOM_MODEL_DATA, CustomModelData(listOf(disc.effectiveCustomModelData.toFloat()), emptyList(), emptyList(), emptyList()))
        
        item.set(DataComponents.ITEM_NAME, Component.literal("♫ ${disc.name} ♫").withStyle(ChatFormatting.AQUA))
        
        val loreLines = mutableListOf<Component>()
        loreLines.add(Component.literal("Artist: ${disc.author}").withStyle(ChatFormatting.YELLOW))
        
        if (disc.durationSeconds > 0) {
            loreLines.add(Component.literal("Duration: ${disc.formattedDuration()}").withStyle(ChatFormatting.GRAY))
        }
        
        if (disc.lore.isNotEmpty()) {
            loreLines.add(Component.empty())
            disc.lore.forEach { line ->
                loreLines.add(Component.literal(line).withStyle(ChatFormatting.DARK_AQUA))
            }
        }
        item.set(DataComponents.LORE, ItemLore(loreLines))
        
        val nbt = CompoundTag()
        nbt.putString("custom_disc_id", disc.id)
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt))
        
        return item
    }

    fun isCustomDisc(stack: ItemStack?): Boolean {
        if (stack == null || stack.isEmpty) return false
        val customData = stack.get(DataComponents.CUSTOM_DATA) ?: return false
        return customData.copyTag().contains("custom_disc_id")
    }

    fun getDiscIdFromItem(stack: ItemStack?): String? {
        if (stack == null || stack.isEmpty) return null
        val customData = stack.get(DataComponents.CUSTOM_DATA) ?: return null
        val nbt = customData.copyTag()
        return if (nbt.contains("custom_disc_id")) nbt.getStringOr("custom_disc_id", "") else null
    }
}
