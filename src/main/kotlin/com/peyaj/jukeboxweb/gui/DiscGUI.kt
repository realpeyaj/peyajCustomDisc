package com.peyaj.jukeboxweb.gui

import com.peyaj.jukeboxweb.PeyajCustomDisc
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class DiscGUI(private val plugin: PeyajCustomDisc) : Listener {

    // Custom Holder to store Page Number safest way
    class CatalogHolder(val page: Int) : InventoryHolder {
        override fun getInventory(): Inventory = throw UnsupportedOperationException("Not supported")
    }

    fun openCatalog(player: Player, page: Int) {
        val discs = plugin.discManager.getAllDiscs().sortedBy { it.name }
        val discsPerPage = 45
        val totalPages = (discs.size + discsPerPage - 1) / discsPerPage
        
        // Clamp page
        val safePage = if (page < 0) 0 else if (page >= totalPages && totalPages > 0) totalPages - 1 else page
        
        val holder = CatalogHolder(safePage)
        val title = Component.text("Disc Catalog - Page ${safePage + 1}/$totalPages", NamedTextColor.DARK_BLUE)
        val inv = Bukkit.createInventory(holder, 54, title)

        // Populate Discs
        val startIndex = safePage * discsPerPage
        val endIndex = minOf(startIndex + discsPerPage, discs.size)

        for (i in startIndex until endIndex) {
            val disc = discs[i]
            val item = plugin.discManager.createDiscItem(disc.id)
            if (item != null) {
                // Add "Click to Get" lore
                item.editMeta { meta ->
                    val lore = meta.lore() ?: mutableListOf()
                    lore.add(Component.empty())
                    lore.add(Component.text("Left-Click to Get", NamedTextColor.GREEN))
                    meta.lore(lore)
                }
                inv.setItem(i - startIndex, item)
            }
        }

        // Navigation
        // 45, 46, 47, 48, 49(Info), 50, 51, 52, 53
        
        // Previous
        if (safePage > 0) {
            val prev = ItemStack(Material.ARROW)
            prev.editMeta { m -> m.displayName(Component.text("Previous Page", NamedTextColor.YELLOW)) }
            inv.setItem(45, prev)
        }

        // Close
        val close = ItemStack(Material.BARRIER)
        close.editMeta { m -> m.displayName(Component.text("Close", NamedTextColor.RED)) }
        inv.setItem(49, close)

        // Next
        if (safePage < totalPages - 1) {
            val next = ItemStack(Material.ARROW)
            next.editMeta { m -> m.displayName(Component.text("Next Page", NamedTextColor.YELLOW)) }
            inv.setItem(53, next)
        }
        
        // Fillers (Optional, maybe gray glass pane?)
        val filler = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        filler.editMeta { m -> m.displayName(Component.empty()) }
        for (i in 45..53) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler)
            }
        }

        player.openInventory(inv)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder
        if (holder !is CatalogHolder) return
        
        event.isCancelled = true // Prevent taking items
        
        val player = event.whoClicked as? Player ?: return
        val item = event.currentItem ?: return
        
        if (event.clickedInventory != event.view.topInventory) return // Verify clicked top inventory

        when (item.type) {
            Material.ARROW -> {
                // Nav
                if (event.slot == 45) { // Prev
                    openCatalog(player, holder.page - 1)
                } else if (event.slot == 53) { // Next
                     openCatalog(player, holder.page + 1)
                }
            }
            Material.BARRIER -> {
                player.closeInventory()
            }
            else -> {
                // Check if it's one of our custom discs (any style)
                if (plugin.discManager.isCustomDisc(item)) {
                    val discId = plugin.discManager.getDiscIdFromItem(item)
                    if (discId != null) {
                        val realItem = plugin.discManager.createDiscItem(discId)
                        if (realItem != null) {
                            player.inventory.addItem(realItem)
                            player.sendMessage(Component.text("Received disc: ", NamedTextColor.GREEN)
                                .append(realItem.itemMeta.displayName() ?: Component.text(discId)))
                        }
                    }
                }
            }
        }
    }
}
