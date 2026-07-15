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
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask

class DiscGUI(private val plugin: PeyajCustomDisc) : Listener {

    // Custom Holder to store Page Number safest way
    class CatalogHolder(val page: Int) : InventoryHolder {
        override fun getInventory(): Inventory = throw UnsupportedOperationException("Not supported")
    }

    private val searchQueries = mutableMapOf<java.util.UUID, String>()
    private val activePreviews = mutableMapOf<java.util.UUID, BukkitTask>()

    fun openCatalog(player: Player, page: Int) {
        val query = searchQueries[player.uniqueId] ?: ""
        val allDiscs = plugin.discManager.getAllDiscs().sortedBy { it.name }
        val discs = if (query.isNotEmpty()) {
            allDiscs.filter { it.name.contains(query, ignoreCase = true) || it.author.contains(query, ignoreCase = true) }
        } else {
            allDiscs
        }
        
        val discsPerPage = 45
        val totalPages = maxOf(1, (discs.size + discsPerPage - 1) / discsPerPage)
        
        // Clamp page
        val safePage = if (page < 0) 0 else if (page >= totalPages) totalPages - 1 else page
        
        val holder = CatalogHolder(safePage)
        val title = if (query.isNotEmpty()) {
            Component.text("Catalog (Search: '$query') - Page ${safePage + 1}/$totalPages", NamedTextColor.DARK_BLUE)
        } else {
            Component.text("Disc Catalog - Page ${safePage + 1}/$totalPages", NamedTextColor.DARK_BLUE)
        }
        val inv = Bukkit.createInventory(holder, 54, title)

        // Populate Discs
        val startIndex = safePage * discsPerPage
        val endIndex = minOf(startIndex + discsPerPage, discs.size)

        for (i in startIndex until endIndex) {
            val disc = discs[i]
            val item = plugin.discManager.createDiscItem(disc.id)
            if (item != null) {
                // Add "Click to Get / Preview" lore
                item.editMeta { meta ->
                    val lore = meta.lore() ?: mutableListOf()
                    lore.add(Component.empty())
                    lore.add(Component.text("Left-Click to Get", NamedTextColor.GREEN))
                    lore.add(Component.text("Right/Middle-Click to Preview (15s)", NamedTextColor.YELLOW))
                    meta.lore(lore)
                }
                inv.setItem(i - startIndex, item)
            }
        }

        // Navigation
        // Previous page arrow
        if (safePage > 0) {
            val prev = ItemStack(Material.ARROW)
            prev.editMeta { m -> m.displayName(Component.text("Previous Page", NamedTextColor.YELLOW)) }
            inv.setItem(45, prev)
        }

        // Search Compass (slot 48)
        val search = ItemStack(Material.COMPASS)
        search.editMeta { m -> m.displayName(Component.text("Search Discs", NamedTextColor.YELLOW)) }
        inv.setItem(48, search)

        // Close Barrier (slot 49)
        val close = ItemStack(Material.BARRIER)
        close.editMeta { m -> m.displayName(Component.text("Close", NamedTextColor.RED)) }
        inv.setItem(49, close)

        // Reset search filter (slot 50, only if active)
        if (query.isNotEmpty()) {
            val clear = ItemStack(Material.MILK_BUCKET)
            clear.editMeta { m -> m.displayName(Component.text("Clear Search Filter", NamedTextColor.LIGHT_PURPLE)) }
            inv.setItem(50, clear)
        }

        // Next page arrow
        if (safePage < totalPages - 1) {
            val next = ItemStack(Material.ARROW)
            next.editMeta { m -> m.displayName(Component.text("Next Page", NamedTextColor.YELLOW)) }
            inv.setItem(53, next)
        }
        
        // Fillers
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
            Material.COMPASS -> {
                searchQueries[player.uniqueId] = ""
                player.closeInventory()
                player.sendMessage(Component.text("Type your search query in chat (or type 'cancel' to exit, 'clear' to clear filter):", NamedTextColor.YELLOW))
            }
            Material.MILK_BUCKET -> {
                searchQueries.remove(player.uniqueId)
                openCatalog(player, 0)
            }
            Material.BARRIER -> {
                player.closeInventory()
            }
            else -> {
                // Check if it's one of our custom discs (any style)
                if (plugin.discManager.isCustomDisc(item)) {
                    val discId = plugin.discManager.getDiscIdFromItem(item)
                    if (discId != null) {
                        val disc = plugin.discManager.getDisc(discId)
                        if (disc != null) {
                            if (event.click.isLeftClick) {
                                val realItem = plugin.discManager.createDiscItem(discId)
                                if (realItem != null) {
                                    player.inventory.addItem(realItem)
                                    player.sendMessage(Component.text("Received disc: ", NamedTextColor.GREEN)
                                        .append(realItem.itemMeta.displayName() ?: Component.text(discId)))
                                }
                            } else {
                                // Local Preview Sound
                                activePreviews.remove(player.uniqueId)?.cancel()
                                player.stopSound(net.kyori.adventure.sound.SoundStop.all())
                                
                                val soundKey = "peyajcustomdisc:disc.$discId"
                                val duration = plugin.config.getInt("catalog.preview-duration", 15)
                                
                                player.playSound(
                                    net.kyori.adventure.sound.Sound.sound(
                                        net.kyori.adventure.key.Key.key(soundKey),
                                        net.kyori.adventure.sound.Sound.Source.RECORD,
                                        1.0f,
                                        1.0f
                                    )
                                )
                                player.sendMessage(Component.text("Previewing '${disc.name}' for ${duration}s...", NamedTextColor.YELLOW))
                                
                                val task = plugin.server.scheduler.runTaskLater(plugin, Runnable {
                                    player.stopSound(net.kyori.adventure.sound.SoundStop.named(net.kyori.adventure.key.Key.key(soundKey)))
                                    player.sendMessage(Component.text("Preview finished.", NamedTextColor.GRAY))
                                    activePreviews.remove(player.uniqueId)
                                }, duration * 20L)
                                
                                activePreviews[player.uniqueId] = task
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val holder = event.inventory.holder
        if (holder !is CatalogHolder) return
        val player = event.player as? Player ?: return
        val task = activePreviews.remove(player.uniqueId)
        if (task != null) {
            task.cancel()
            player.stopSound(net.kyori.adventure.sound.SoundStop.all())
            player.sendMessage(Component.text("Preview stopped.", NamedTextColor.GRAY))
        }
    }

    @EventHandler
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        val marker = searchQueries[player.uniqueId]
        if (marker == null) return
        
        event.isCancelled = true
        val query = event.message.trim()
        
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (query.equals("cancel", ignoreCase = true)) {
                searchQueries.remove(player.uniqueId)
                player.sendMessage(Component.text("Search cancelled.", NamedTextColor.RED))
                openCatalog(player, 0)
            } else if (query.equals("clear", ignoreCase = true)) {
                searchQueries.remove(player.uniqueId)
                player.sendMessage(Component.text("Search filter cleared.", NamedTextColor.GREEN))
                openCatalog(player, 0)
            } else {
                searchQueries[player.uniqueId] = query
                player.sendMessage(Component.text("Filtering by: '$query'", NamedTextColor.GREEN))
                openCatalog(player, 0)
            }
        })
    }
}
